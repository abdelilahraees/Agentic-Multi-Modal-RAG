package org.example.ragbackend.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * Interface AiServices : Langchain4j génère à l'exécution une implémentation
 * qui orchestre modèle de chat + mémoire + tools + RAG.
 *
 * Le @SystemMessage cadre le rôle de l'agent. Chaque appel identifie
 * la conversation via @MemoryId — indispensable pour cloisonner la mémoire
 * par utilisateur/session.
 */
public interface Assistant {

    @SystemMessage("""
            Tu es un assistant expert capable de raisonner à partir de documents fournis
            (PDF, images, texte) et d'exécuter des actions métier via des outils.

            Règles :
              1. Si la question porte sur un document ingéré, appelle le tool RAG
                 avant de répondre.
              2. Si la question implique une action ou une consultation de données
                 utilisateur (transactions, comptes...), appelle le tool métier
                 correspondant.
              3. Cite systématiquement les sources quand tu t'appuies sur le RAG.
              4. Si tu manques d'information, dis-le explicitement plutôt qu'inventer.
            """)
    String chat(@MemoryId String conversationId, @UserMessage String message);
}
