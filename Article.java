import java.util.List;

public class Article {

    private String uuid;
    private String title;
    private String author;
    private String url;
    private String text;
    private String published;
    private String language;
    private List<String> categories;

    private Article(Builder builder) {
        this.uuid = builder.uuid;
        this.title = builder.title;
        this.author = builder.author;
        this.url = builder.url;
        this.text = builder.text;
        this.published = builder.published;
        this.language = builder.language;
        this.categories = builder.categories;
    }

    public static class Builder {
        private String uuid;
        private String title;
        private String author;
        private String url;
        private String text;
        private String published;
        private String language;
        private List<String> categories;

        public Builder uuid(String uuid) {
            this.uuid = uuid;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder author(String author) {
            this.author = author;
            return this;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder published(String published) {
            this.published = published;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder categories(List<String> categories) {
            this.categories = categories;
            return this;
        }

        public Article build() {
            return new Article(this);
        }
    }

    // Optional getters
    public String getUuid() {
        return uuid;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getUrl() {
        return url;
    }

    public String getText() {
        return text;
    }

    public String getPublished() {
        return published;
    }

    public String getLanguage() {
        return language;
    }

    public List<String> getCategories() {
        return categories;
    }
}