import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class JsonInput {
    public static ArrayList<Article> deserializeAccounts(String jsonPath) {

        try {
            String content = new String((Files.readAllBytes(Paths.get(jsonPath))));

            // JSON simple implementation
            JSONParser parser = new JSONParser();

            // root
            JSONArray articlesArray = (JSONArray) parser.parse(content);;

            ArrayList<Article> articles = new ArrayList<>();

            for (Object i : articlesArray) {
                JSONObject obj= (JSONObject) i;

                String uuid = (String) obj.get("uuid");
                String title = (String) obj.get("title");
                String author = (String) obj.get("author");
                String url = (String) obj.get("url");
                String text = (String) obj.get("text");
                String published = (String) obj.get("published");
                String language = (String) obj.get("language");

                List<String> categories = new ArrayList<>();
                JSONArray categoriesArray = (JSONArray) obj.get("categories");
                if (categoriesArray != null) {
                    for (Object category : categoriesArray) {
                        categories.add((String) category);
                    }
                }

                Article article = new Article.Builder()
                        .uuid(uuid)
                        .title(title)
                        .author(author)
                        .url(url)
                        .text(text)
                        .published(published)
                        .language(language)
                        .categories(categories)
                        .build();

                articles.add(article);
            }
            return articles;

        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

}