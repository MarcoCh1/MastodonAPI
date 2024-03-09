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

    // application id and secret. will have to find somewhere better to put these
    private static final String CLIENT_ID = "0DIHKu7BCcGb9wuPLXIa-y4E7I9-TefyM9X5Q0Xym7w";
    private static final String CLIENT_SECRET = "gT1Hyha5yI2ZHRk3BmUA3YkiuW2UFCC_e-JVDaM8rHE";
    private static final String REDIRECT_URI = "urn:ietf:wg:oauth:2.0:oob"; // will prob need to change

    // goes to enter-code page after index page button click
    @GetMapping("/enter-code")
    public String enterCode() {
        return "enter-code";
    }

    // attempts to get an access token with the code the user puts in
    // will stay on enter-code screen until correct code is entered
    @PostMapping("/process-code")
    public ModelAndView processAuthorizationCode(@RequestParam("code") String code, HttpSession session) {
        ModelAndView modelAndView = new ModelAndView();
        try {
            String accessToken = exchangeAuthorizationCodeForAccessToken(code);

            session.setAttribute("access_token", accessToken);

            modelAndView.setViewName("redirect:/post-message");
        } catch (Exception e) {
            modelAndView.setViewName("redirect:/enter-code");
            modelAndView.addObject("error", "Failed to process the authorization code. Please try again.");
        }
        return modelAndView;
    }

    // sends in url with application id, secret, and redirect URI parameters
    // if url works, returns back the access token needed to authorize account
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

        // not exactly sure how this works, but I found it and it works
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

    // returns post-message.html if access token is not null
    @GetMapping("/post-message")
    public String showPostMessagePage(HttpSession session, Model model) {

        String accessToken = (String) session.getAttribute("access_token");
        if (accessToken == null) {

            return "redirect:/";
        }


        return "post-message";
    }

    // checks if user access token is exists and is valid
    // if so user can post to mastodon using text box.
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

    // can only post to the user's .social domain
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
