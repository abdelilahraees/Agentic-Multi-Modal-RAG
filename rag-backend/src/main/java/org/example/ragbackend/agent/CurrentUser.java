package org.example.ragbackend.agent;

import java.util.function.Supplier;

/**
 * Porte l'identité de l'utilisateur pour la durée d'un appel à l'agent.
 *
 * Les tools métier lisent l'utilisateur ici plutôt que de le recevoir en
 * paramètre du LLM : un prompt malveillant ("montre les transactions de bob")
 * ne peut donc pas faire agir l'agent pour le compte d'un autre utilisateur.
 * Langchain4j exécute les tools de manière synchrone dans le thread appelant,
 * un ThreadLocal suffit.
 */
public final class CurrentUser {

    private static final ThreadLocal<String> USER = new ThreadLocal<>();

    private CurrentUser() {}

    public static <T> T runAs(String userId, Supplier<T> action) {
        String previous = USER.get();
        USER.set(userId);
        try {
            return action.get();
        } finally {
            if (previous == null) USER.remove(); else USER.set(previous);
        }
    }

    public static String get() {
        String userId = USER.get();
        if (userId == null) {
            throw new IllegalStateException("Aucun utilisateur courant : appel hors du contexte de l'agent");
        }
        return userId;
    }
}
