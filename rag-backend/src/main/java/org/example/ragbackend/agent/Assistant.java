package org.example.ragbackend.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * Interface AiServices : Langchain4j génère à l'exécution une implémentation
 * qui orchestre modèle de chat + mémoire + tools + RAG.
 *
 * Le @SystemMessage cadre le rôle de l'agent. Chaque appel identifie
 * la conversation via @MemoryId — indispensable pour cloisonner la mémoire
 * par utilisateur/session. Le retour {@link Result} expose, en plus de la
 * réponse, les sources RAG injectées et les tools exécutés.
 */
public interface Assistant {

    @SystemMessage("""
            Tu es un assistant expert capable de raisonner à partir de documents fournis
            (PDF, images, texte) et d'exécuter des actions métier via des outils.

            Règles :
              1. Si la question porte sur un document ingéré, appuie-toi sur le contexte
                 fourni et appelle le tool de recherche documentaire si nécessaire.
              2. Si la question implique une action ou une consultation de données
                 utilisateur (transactions, dépenses...), appelle le tool métier
                 correspondant. Les tools agissent toujours pour l'utilisateur connecté.
              3. Avant de créer une transaction, assure-toi d'avoir un libellé et un montant
                 explicites ; sinon demande une précision.
              4. Cite systématiquement les sources (nom du fichier) quand tu t'appuies sur le RAG.
              5. Si tu manques d'information, dis-le explicitement plutôt qu'inventer.
              6. Réponds dans la langue de l'utilisateur, en Markdown concis.
            """)
    Result<String> chat(@MemoryId String conversationId, @UserMessage String message);
}
