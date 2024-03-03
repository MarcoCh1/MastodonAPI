package edu.missouristate.mastodonapijavamaster;

import jakarta.servlet.http.HttpSession;
import org.json.JSONObject;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

@Controller
public class MastodonController {

    private static final String MASTODON_INSTANCE_URL = "https://mastodon.example";
    private static final String CLIENT_ID = "BSWItFq7Qb0B6Xhe0Ok8fnnhNPuMehvW8Zs8OYx06yY";
    private static final String CLIENT_SECRET = "cazj9vt2FrDf4WgTAp5Iv9aVVDVgtp25eO3c50J8e1k";
    private static final String REDIRECT_URI = "urn:ietf:wg:oauth:2.0:oob"; // Adjust based on actual redirect URI

//    public void registerApplication() throws IOException {
//        URL url = new URL(MASTODON_INSTANCE_URL + "/api/v1/apps");
//        HttpURLConnection con = (HttpURLConnection) url.openConnection();
//        con.setRequestMethod("POST");
//        con.setDoOutput(true);
//        con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
//
//        String postParams = String.format(
//                "client_name=%s&redirect_uris=%s&scopes=%s&website=%s",
//                URLEncoder.encode("Test Application", "UTF-8"),
//                URLEncoder.encode("urn:ietf:wg:oauth:2.0:oob", "UTF-8"),
//                URLEncoder.encode("read write push", "UTF-8"),
//                URLEncoder.encode("https://myapp.example", "UTF-8")
//        );
//
//        OutputStream os = con.getOutputStream();
//        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
//        writer.write(postParams);
//        writer.flush();
//        writer.close();
//        os.close();
//
//        int responseCode = con.getResponseCode();
//        if (responseCode == HttpURLConnection.HTTP_OK) { //success
//            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
//            String inputLine;
//            StringBuilder response = new StringBuilder();
//
//            while ((inputLine = in.readLine()) != null) {
//                response.append(inputLine);
//            }
//            in.close();
//
//            // Parse the JSON response to extract client_id and client_secret
//            JSONObject jsonResponse = new JSONObject(response.toString());
//            String clientId = jsonResponse.getString("client_id");
//            String clientSecret = jsonResponse.getString("client_secret");
//
//            // Here you should store the clientId and clientSecret securely for future use
//            System.out.println("Application registered successfully. Client ID: " + clientId + ", Client Secret: " + clientSecret);
//        } else {
//            System.out.println("Failed to register application. HTTP error code : " + responseCode);
//        }
//    }


    @GetMapping("/authorize-mastodon")
    public ModelAndView authorizeMastodon() {
        ModelAndView modelAndView = new ModelAndView("redirect:" + buildAuthorizationUrl());
        return modelAndView;
    }

    private String buildAuthorizationUrl() {
        try {
            return "https://mastodon.social/oauth/authorize" +
                    "?response_type=code" +
                    "&client_id=" + CLIENT_ID +
                    "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, "UTF-8") +
                    "&scope=read+write";
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
    }

    @PostMapping("/mastodon-callback")
    public ModelAndView mastodonCallback(@RequestParam("code") String authorizationCode, HttpSession session) {
        ModelAndView modelAndView = new ModelAndView("result");
        try {
            String accessToken = exchangeAuthorizationCodeForAccessToken(authorizationCode);
            session.setAttribute("mastodon_access_token", accessToken);
            modelAndView.addObject("message", "Mastodon authorization successful!");
        } catch (Exception e) {
            modelAndView.setViewName("error");
            modelAndView.addObject("message", "Failed to authorize Mastodon: " + e.getMessage());
        }
        return modelAndView;
    }

    @GetMapping("/enter-code")
    public String enterCode() {
        return "enter-code"; // Name of the Thymeleaf template without the .html extension
    }

    @PostMapping("/process-code")
    public ModelAndView processAuthorizationCode(@RequestParam("code") String code, HttpSession session) {
        ModelAndView modelAndView = new ModelAndView();
        try {
            // Exchange the authorization code for an access token
            String accessToken = exchangeAuthorizationCodeForAccessToken(code);
            // Store the access token in the session or a secure place
            session.setAttribute("access_token", accessToken);
            // Redirect to the message posting page
            modelAndView.setViewName("redirect:/post-message");
        } catch (Exception e) {
            // On failure, redirect back to the code entry page with an error message
            modelAndView.setViewName("redirect:/enter-code");
            modelAndView.addObject("error", "Failed to process the authorization code. Please try again.");
        }
        return modelAndView;
    }

    private String exchangeAuthorizationCodeForAccessToken(String authorizationCode) throws IOException {
        String tokenUrl = "https://mastodon.social/oauth/token";
        String params = "client_id=" + CLIENT_ID +
                "&client_secret=" + CLIENT_SECRET +
                "&grant_type=authorization_code" +
                "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, "UTF-8") +
                "&code=" + authorizationCode;

        URL url = new URL(tokenUrl);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.getOutputStream().write(params.getBytes());

        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        JSONObject jsonResponse = new JSONObject(response.toString());
        return jsonResponse.getString("access_token");
    }

    @GetMapping("/post-message")
    public String showPostMessagePage(HttpSession session, Model model) {
        // Optional: Check if the user has an access token stored in the session
        String accessToken = (String) session.getAttribute("access_token");
        if (accessToken == null) {
            // If no access token, redirect to start the authorization process again or to an error page
            return "redirect:/"; // Adjust as necessary, for example, to an error page or login page
        }

        // If there is an access token, return the view name of the post message page
        return "post-message"; // This should match the name of your Thymeleaf template (without .html extension)
    }

    @PostMapping("/post-to-mastodon")
    public ModelAndView postToMastodon(@RequestParam("message") String message, HttpSession session) {
        ModelAndView modelAndView = new ModelAndView("result");
        try {
            String accessToken = (String) session.getAttribute("access_token");
            if (accessToken == null) {
                throw new Exception("No access token available. Please authorize first.");
            }
            postMessageToMastodon(message, accessToken);
            modelAndView.addObject("message", "Message posted to Mastodon successfully!");
        } catch (Exception e) {
            modelAndView.setViewName("error");
            modelAndView.addObject("error", "Failed to post message to Mastodon: " + e.getMessage());
        }
        return modelAndView;
    }

    private void postMessageToMastodon(String message, String accessToken) throws IOException {
        URL postUrl = new URL("https://mastodon.social/api/v1/statuses");
        HttpURLConnection postCon = (HttpURLConnection) postUrl.openConnection();
        postCon.setRequestMethod("POST");
        postCon.setRequestProperty("Authorization", "Bearer " + accessToken);
        postCon.setDoOutput(true);
        String postParams = "status=" + URLEncoder.encode(message, "UTF-8");
        postCon.getOutputStream().write(postParams.getBytes());

        int postResponseCode = postCon.getResponseCode();
        if (postResponseCode != HttpURLConnection.HTTP_OK) {
            throw new RuntimeException("Failed to post to Mastodon, response code: " + postResponseCode);
        }
    }

}
