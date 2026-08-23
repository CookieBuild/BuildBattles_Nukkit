package com.cookiebuild.buildbattles.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class BuildBattlesUiLocalizationTest {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\d+}");
    private static final ResourceBundle.Control NO_FALLBACK =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    @Test
    void completePlayerLocalesHaveExactKeyAndPlaceholderParity() {
        ResourceBundle english = bundle(Locale.ENGLISH);
        for (Locale locale : Set.of(Locale.FRENCH, Locale.of("es"), Locale.of("pt", "BR"),
                Locale.of("bg"), Locale.of("hi"))) {
            ResourceBundle translated = bundle(locale);
            assertEquals(english.keySet(), translated.keySet(), "keys for " + locale);
            for (String key : english.keySet()) {
                assertEquals(placeholders(english.getString(key)), placeholders(translated.getString(key)),
                        key + " placeholders for " + locale);
            }
        }
    }

    @Test
    void criticalVoteAndScoreboardCopyCannotReturnAsHardcodedEnglish() throws IOException {
        Path path = Path.of("src/main/java/com/cookiebuild/buildbattles/game/BuildBattlesGame.java");
        if (!Files.exists(path)) path = Path.of("BuildBattles").resolve(path);
        String source = Files.readString(path);

        for (String forbidden : Set.of("Use to vote for this theme", "Vote \" + candidate",
                "§6Theme:", "§6Phase:", "§6Players:", "§6Time:", "§6Blocks:",
                "§6Plot:", "/floor changes the floor", "Use to submit your one vote",
                "Component.text(candidate", "bb.theme.vote_hover\", candidate")) {
            assertFalse(source.contains(forbidden), forbidden);
        }
    }

    private static ResourceBundle bundle(Locale locale) {
        return ResourceBundle.getBundle("buildbattles_messages", locale,
                BuildBattlesUiLocalizationTest.class.getClassLoader(), NO_FALLBACK);
    }

    private static Set<String> placeholders(String value) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) result.add(matcher.group());
        return result;
    }
}
