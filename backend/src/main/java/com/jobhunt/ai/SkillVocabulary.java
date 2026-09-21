package com.jobhunt.ai;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Domain vocabulary used by the deterministic matcher.
 *
 * <p>This is a curated list of terms that actually appear in job adverts — it is not user
 * data and nothing is inferred about a candidate from it. Matching is literal: a skill is
 * "present" only if the word appears in the text the user supplied.
 */
public final class SkillVocabulary {

    /** Display names. Longest entries are matched first so "Spring Boot" wins over "Spring". */
    private static final List<String> SKILLS = List.of(
            // Languages
            "Java", "Kotlin", "Scala", "Python", "JavaScript", "TypeScript", "Go", "Rust", "C#", "C++",
            "Ruby", "PHP", "Swift", "SQL", "Bash", "Shell",
            // Backend / frameworks
            "Spring Boot", "Spring", "Hibernate", "JPA", "JUnit", "Mockito", "REST API", "REST",
            "GraphQL", "gRPC", "Microservices", "Django", "Flask", "FastAPI", "Node.js", "Express",
            "ASP.NET", "Rails",
            // Frontend
            "React", "Redux", "Angular", "Vue", "Next.js", "Svelte", "HTML", "CSS", "Sass",
            "Tailwind", "Vite", "Webpack", "Jest", "Vitest", "Cypress", "Playwright", "Selenium",
            "React Native",
            // Data / storage
            "PostgreSQL", "MySQL", "MongoDB", "Redis", "Elasticsearch", "Snowflake", "BigQuery",
            "Cassandra", "DynamoDB", "Oracle", "SQL Server", "Flyway", "Liquibase",
            // Messaging / streaming
            "Kafka", "RabbitMQ", "ActiveMQ", "SQS", "SNS", "Pub/Sub", "Event-driven",
            "Streaming", "Message Queue",
            // Cloud / platform
            "AWS", "Azure", "GCP", "Kubernetes", "Docker", "Terraform", "Ansible", "Helm",
            "Lambda", "EC2", "S3", "CloudFormation", "Serverless", "Linux", "Nginx",
            // Delivery / practice
            "CI/CD", "Jenkins", "GitHub Actions", "GitLab CI", "Git", "Agile", "Scrum", "Kanban",
            "Jira", "Confluence", "TDD", "BDD", "Domain-Driven Design", "Design Patterns",
            "Code Review", "Pair Programming", "Refactoring", "OAuth", "JWT", "SSO", "SAML",
            "OpenAPI", "Swagger",
            // Quality / ops
            "Observability", "Prometheus", "Grafana", "Datadog", "Splunk", "Sentry", "Monitoring",
            "Performance Tuning", "Caching", "Load Testing", "Incident Response", "On-call",
            // Data / ML
            "Pandas", "NumPy", "scikit-learn", "TensorFlow", "PyTorch", "Machine Learning", "NLP",
            "ETL", "Airflow", "Spark", "Hadoop", "dbt", "Data Modelling", "Data Warehouse",
            "Tableau", "Power BI", "Looker", "Excel", "A/B Testing",
            // Product / business
            "Product Roadmap", "Stakeholder Management", "User Research", "Requirements Gathering",
            "Business Analysis", "Financial Modelling", "Forecasting", "Budgeting", "Risk Management",
            "Salesforce", "SAP", "Workday", "SEO", "Content Marketing", "CRM",
            // Design / accessibility
            "Figma", "Sketch", "Adobe XD", "Accessibility", "WCAG", "Design System", "Wireframing",
            "Prototyping", "Usability Testing",
            // Soft skills that appear verbatim in adverts
            "Mentoring", "Communication", "Collaboration", "Problem Solving", "Ownership");

    /** Matched longest-first so multi-word skills are not shadowed by their prefixes. */
    private static final List<String> ORDERED = SKILLS.stream()
            .distinct()
            .sorted(Comparator.comparingInt(String::length).reversed())
            .toList();

    private static final Pattern WORD = Pattern.compile("[a-z0-9+#./-]{4,}");
    private static final Set<String> STOPWORDS = Set.of(
            "with", "that", "this", "from", "they", "will", "have", "your", "you", "and", "the",
            "for", "are", "our", "who", "what", "when", "work", "team", "role", "job", "able",
            "must", "need", "plus", "well", "good", "strong", "experience", "years", "year",
            "about", "into", "across", "using", "used", "more", "than", "other", "such", "them",
            "their", "there", "these", "those", "would", "should", "could", "also", "help",
            "join", "looking", "want", "like", "skills", "skill", "knowledge", "ability");

    private SkillVocabulary() {
    }

    public static List<String> all() {
        return ORDERED;
    }

    /** Significant lowercase words, used as a second, weaker overlap signal. */
    public static Set<String> significantWords(String text) {
        Set<String> words = new java.util.LinkedHashSet<>();
        if (text == null) {
            return words;
        }
        var matcher = WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String word = matcher.group();
            if (!STOPWORDS.contains(word)) {
                words.add(word);
            }
        }
        return words;
    }
}
