package mnfu.clantag;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class MojangApi {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final Gson GSON = new Gson();

    public static String getUsernameFromUuid(String uuid) throws Exception {
        String stripped = uuid.replace("-", "");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        "https://sessionserver.mojang.com/session/minecraft/profile/" + stripped
                ))
                .GET()
                .build();

        HttpResponse<String> response = CLIENT.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new Exception("status code bad :(");
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        return json.get("name").getAsString();
    }
}

