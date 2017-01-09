
package com.linecorp.example.linebot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.util.Map;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.CharBuffer;
import java.time.LocalDateTime;
import java.sql.SQLException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.*;

import org.apache.http.HttpResponse;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.commons.io.IOUtils;

import org.json.JSONObject;
import org.json.JSONArray;
import com.google.gson.Gson;

import okhttp3.ResponseBody;
import retrofit2.Response;

import com.linecorp.bot.model.PushMessage;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.message.ImageMessage;
import com.linecorp.bot.model.response.BotApiResponse;
import com.linecorp.bot.client.LineSignatureValidator;
import com.linecorp.bot.client.LineMessagingServiceBuilder;


import com.cloudinary.*;
import com.cloudinary.utils.ObjectUtils;

@RestController
@RequestMapping(value="/linebot")
public class LineBotController
{
    @Autowired
    @Qualifier("com.cloudinary.cloud_name")
    String cCloudName;
    
    @Autowired
    @Qualifier("com.cloudinary.api_key")
    String cApiKey;
    
    @Autowired
    @Qualifier("com.cloudinary.api_secret")
    String cApiSecret;
    
    @Autowired
    @Qualifier("com.linecorp.channel_secret")
    String lChannelSecret;
    
    @Autowired
    @Qualifier("com.linecorp.channel_access_token")
    String lChannelAccessToken;
    
    @RequestMapping(value="/callback", method=RequestMethod.POST)
    public ResponseEntity<String> callback(
        @RequestHeader("X-Line-Signature") String aXLineSignature,
        @RequestBody String aPayload)
    {
        // compose body
        final String text=String.format("The Signature is: %s",
            (aXLineSignature!=null && aXLineSignature.length() > 0) ? aXLineSignature : "N/A");
        
        System.out.println(text);
        System.out.println("Cloudinary Cloud Name: " + cCloudName);
        System.out.println("Cloudinary API Key: " + cApiKey);
        System.out.println("Cloudinary API Secret: " + cApiSecret);
        System.out.println("Channel Secret: " + lChannelSecret);
        System.out.println("Channel Access Token: " + lChannelAccessToken);
        
        final boolean valid=new LineSignatureValidator(lChannelSecret.getBytes()).validateSignature(aPayload.getBytes(), aXLineSignature);
        
        System.out.println("The signature is: " + (valid ? "valid" : "tidak valid"));
        
        //Get events from source
        if(aPayload!=null && aPayload.length() > 0)
        {
            System.out.println("Payload: " + aPayload);
        }
        
        Gson gson = new Gson();
        Payload payload = gson.fromJson(aPayload, Payload.class);
        
        //Variable initialization
        String msgText = " ";
        String upload_url = " ";
        String mJSON = " ";
        String idTarget = " ";
        String eventType = payload.events[0].type;
        
        if (eventType.equals("join")){
            if (payload.events[0].source.type.equals("group")){
                replyToUser(payload.events[0].replyToken, "Hello Group");
            }
            if (payload.events[0].source.type.equals("room")){
                replyToUser(payload.events[0].replyToken, "Hello Room");
            }
        } else if (eventType.equals("message")){
            if (payload.events[0].source.type.equals("group")){
                idTarget = payload.events[0].source.groupId;
            } else if (payload.events[0].source.type.equals("room")){
                idTarget = payload.events[0].source.roomId;
            } else if (payload.events[0].source.type.equals("user")){
                idTarget = payload.events[0].source.userId;
            }
            
            //Parsing message from user
            if (!payload.events[0].message.type.equals("text")){
                upload_url = getUserContent(payload.events[0].message.id, payload.events[0].source.userId);
                pushPoster(idTarget, upload_url);
            } else {
                msgText = payload.events[0].message.text;
                msgText = msgText.toLowerCase();
                
                if (!msgText.contains("bot leave")){
                    replyToUser(payload.events[0].replyToken, "Unknown keyword");
                } else {
                    if (payload.events[0].source.type.equals("group")){
                        leaveGR(payload.events[0].source.groupId, "group");
                    } else if (payload.events[0].source.type.equals("room")){
                        leaveGR(payload.events[0].source.roomId, "room");
                    }
                }
            }
        }
         
        return new ResponseEntity<String>(HttpStatus.OK);
    }
    
    private void replyToUser(String rToken, String messageToUser){
        TextMessage textMessage = new TextMessage(messageToUser);
        ReplyMessage replyMessage = new ReplyMessage(rToken, textMessage);
        try {
            Response<BotApiResponse> response = LineMessagingServiceBuilder
                .create(lChannelAccessToken)
                .build()
                .replyMessage(replyMessage)
                .execute();
            System.out.println("Reply Message: " + response.code() + " " + response.message());
        } catch (IOException e) {
            System.out.println("Exception is raised ");
            e.printStackTrace();
        }
    }
    
    private String getUserContent(String messageId, String source_id){
        Cloudinary cloudinary = new Cloudinary("cloudinary://"+cApiKey+":"+cApiSecret+"@"+cCloudName);
        String uploadURL = " ";
        try {
            Response<ResponseBody> response = LineMessagingServiceBuilder
                .create(lChannelAccessToken)
                .build()
                .getMessageContent(messageId)
                .execute();
            if (response.isSuccessful()) {
                ResponseBody content = response.body();
                InputStream imageStream = content.byteStream();
                Path path = Files.createTempFile(messageId, ".jpg");
                try (FileOutputStream out = new FileOutputStream(path.toFile())) {
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = imageStream.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                } catch (Exception e) {
                    System.out.println("Exception is raised ");
                }
                Map uploadResult = cloudinary.uploader().upload(path.toFile(), ObjectUtils.emptyMap());
                System.out.println(uploadResult.toString());
                JSONObject jUpload = new JSONObject(uploadResult);
                uploadURL = jUpload.getString("secure_url");
            } else {
                System.out.println(response.code() + " " + response.message());
            }
        } catch (IOException e) {
            System.out.println("Exception is raised ");
            e.printStackTrace();
        }
        return uploadURL;
    }
    
    private void pushPoster(String sourceId, String poster_url){
        ImageMessage imageMessage = new ImageMessage(poster_url, poster_url);
        PushMessage pushMessage = new PushMessage(sourceId,imageMessage);
        try {
            Response<BotApiResponse> response = LineMessagingServiceBuilder
            .create(lChannelAccessToken)
            .build()
            .pushMessage(pushMessage)
            .execute();
            System.out.println(response.code() + " " + response.message());
        } catch (IOException e) {
            System.out.println("Exception is raised ");
            e.printStackTrace();
        }
    }
    
    private void leaveGR(String id, String type){
        try {
            if (type.equals("group")){
                Response<BotApiResponse> response = LineMessagingServiceBuilder
                    .create(lChannelAccessToken)
                    .build()
                    .leaveGroup(id)
                    .execute();
                System.out.println(response.code() + " " + response.message());
            } else if (type.equals("room")){
                Response<BotApiResponse> response = LineMessagingServiceBuilder
                    .create(lChannelAccessToken)
                    .build()
                    .leaveRoom(id)
                    .execute();
                System.out.println(response.code() + " " + response.message());
            }
        } catch (IOException e) {
            System.out.println("Exception is raised ");
            e.printStackTrace();
        }
    }
}
