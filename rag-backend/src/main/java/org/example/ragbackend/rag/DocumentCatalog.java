package org.example.ragbackend.rag;

import lombok.RequiredArgsConstructor;
import org.example.ragbackend.config.AiConfig.AiProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Vue "catalogue" de la base vectorielle : regroupe les segments par document
 * à partir des métadonnées JSON écrites par {@link RagIngestionService}.
 * L'EmbeddingStore Langchain4j n'expose pas ce type d'agrégat, d'où le SQL direct.
 */
@Repository
@RequiredArgsConstructor
public class DocumentCatalog {

    private final JdbcTemplate jdbc;
    private final AiProperties props;

    public List<DocumentSummary> list() {
        String table = props.getRag().getTable();
        if (!table.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalStateException("Nom de table invalide : " + table);
        }
        String sql = """
                SELECT metadata->>'documentId' AS document_id,
                       MAX(metadata->>'source')     AS source,
                       MAX(metadata->>'owner')      AS owner,
                       MAX(metadata->>'mime')       AS mime,
                       MAX(metadata->>'kind')       AS kind,
                       MAX(metadata->>'ingestedAt') AS ingested_at,
                       COUNT(*)                     AS segments
                FROM %s
                WHERE metadata->>'documentId' IS NOT NULL
                GROUP BY metadata->>'documentId'
                ORDER BY ingested_at DESC
                """.formatted(table);

        return jdbc.query(sql, (rs, i) -> new DocumentSummary(
                rs.getString("document_id"),
                rs.getString("source"),
                rs.getString("owner"),
                rs.getString("mime"),
                rs.getString("kind"),
                rs.getString("ingested_at"),
                rs.getInt("segments")));
    }

    public record DocumentSummary(String documentId, String source, String owner, String mimeType,
                                  String kind, String ingestedAt, int segments) {}
}
