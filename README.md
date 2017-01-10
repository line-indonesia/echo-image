# echo-image #

This repository demonstrates how to create a bot application with **Spring framework** and integrated with **LINE Messaging API**, **LINE Bot SDK** and **Cloudinary image storage** to echo the image which user sent via LINE Chat to your bot application. This application is deployed in **Heroku**.

### How do I get set up? ###
* Make LINE@ Account with Messaging API enabled
> [LINE Business Center](https://business.line.me/en/)

* Register your Webhook URL
	1. Open [LINE Developer](https://developers.line.me/)
	2. Choose your channel
	3. Edit "Basic Information"
* Create Cloudinary account
> [Cloudinary](http://cloudinary.com)

* Add `application.properties` file in *src/main/resources* directory, and fill it with your Cloudinary's cloud name, api key, and api secret, channel secret and channel access token, like the following:

	```ini
com.cloudinary.cloud_name=<your_cloud_name>
com.cloudinary.api_key=<your_api_key>
com.cloudinary.api_secret=<your_api_secret>
com.linecorp.channel_secret=<your_channel_secret>
com.linecorp.channel_access_token=<your_channel_access_token>
```

* Get user's image

	```java
	Response<ResponseBody> response =
        LineMessagingServiceBuilder
                .create("<channel access token>")
                .build()
                .getMessageContent("<messageId>")
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
	} else {
    	System.out.println(response.code() + " " + response.message());
	}
	```

* Store at Cloudinary

	```java
	Map uploadResult = cloudinary.uploader().upload(path.toFile(), ObjectUtils.emptyMap());
    System.out.println(uploadResult.toString());
    JSONObject jUpload = new JSONObject(uploadResult);
    uploadURL = jUpload.getString("secure_url");
	```
	**INFO** Image needs to be store because LINE Messaging API needs image URL to push or reply the image message to user.
	**INFO** URL must be secure **(HTTPS)**, Therefore user *secure_url* in JSON Object response from cloudinary

* Push image to user

	```java
	Response<BotApiResponse> response = LineMessagingServiceBuilder
            .create(lChannelAccessToken)
            .build()
            .pushMessage(pushMessage)
            .execute();
   	System.out.println(response.code() + " " + response.message());
	```


* Compile

	`gradle clean build`

* Add to Git Repositories
	
	`git add .`

* Commit changes

	`git commit -m "Your Messages"`

* Deploy

	`git push heroku master`

* Run Server

	`$ heroku ps:scale web=1`

* Open logs

	`heroku logs --tail`


### How do I contribute? ###

* Add your name and e-mail address into CONTRIBUTORS.txt
