package org.waveprotocol.box.server.waveserver.lucene9;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.junit.Test;
import org.waveprotocol.wave.model.id.WaveId;

/**
 * Regression tests for Lucene9 tag/mention field indexing.
 *
 * <p>These tests guard against bugs where tags or mentions added to a wave after startup
 * become invisible to search — the root cause being that {@code WaveDocumentBuilder} must
 * store the correct fields and those fields must be queryable via {@link TermQuery}.
 */
public final class Lucene9DocumentBuilderTest {

  private static WaveDocumentBuilder newBuilder() {
    Config config = ConfigFactory.parseMap(java.util.Map.of(
        "core.lucene9_vector_dimensions", 0,
        "core.lucene9_vector_similarity", "cosine"));
    return new WaveDocumentBuilder(config, new NoOpWaveEmbeddingProvider());
  }

  private static WaveMetadataExtractor.WaveMetadata metadataWithTags(
      WaveId waveId, String... tags) {
    Set<String> tagSet = new LinkedHashSet<>(Arrays.asList(tags));
    return new WaveMetadataExtractor.WaveMetadata(
        waveId, "conv+root",
        new LinkedHashSet<>(Arrays.asList("user@example.com")),
        new LinkedHashSet<>(Arrays.asList("user@example.com")),
        "user@example.com", tagSet, new LinkedHashSet<>(), new LinkedHashSet<>(),
        "", "", "", 1000L, 2000L);
  }

  private static WaveMetadataExtractor.WaveMetadata metadataWithMentions(
      WaveId waveId, String... mentions) {
    Set<String> mentionSet = new LinkedHashSet<>(Arrays.asList(mentions));
    return new WaveMetadataExtractor.WaveMetadata(
        waveId, "conv+root",
        new LinkedHashSet<>(Arrays.asList("user@example.com")),
        new LinkedHashSet<>(Arrays.asList("user@example.com")),
        "user@example.com", new LinkedHashSet<>(), mentionSet, new LinkedHashSet<>(),
        "", "", "", 1000L, 2000L);
  }

  private static WaveMetadataExtractor.WaveMetadata metadataWithTaskAssignees(
      WaveId waveId, String... assignees) {
    Set<String> assigneeSet = new LinkedHashSet<>(Arrays.asList(assignees));
    return new WaveMetadataExtractor.WaveMetadata(
        waveId, "conv+root",
        new LinkedHashSet<>(Arrays.asList("user@example.com")),
        new LinkedHashSet<>(Arrays.asList("user@example.com")),
        "user@example.com", new LinkedHashSet<>(), new LinkedHashSet<>(), assigneeSet,
        "", "", "", 1000L, 2000L);
  }

  // --- WaveDocumentBuilder field-level tests ---

  @Test
  public void tagFieldsAreStoredInDocument() {
    WaveDocumentBuilder builder = newBuilder();
    WaveId waveId = WaveId.of("example.com", "w+tags");
    Document doc = builder.build(metadataWithTags(waveId, "urgent", "project"), null);

    IndexableField[] tagFields = doc.getFields(Lucene9FieldNames.TAG);
    assertEquals(2, tagFields.length);
    Set<String> storedTags = new HashSet<>();
    for (IndexableField f : tagFields) storedTags.add(f.stringValue());
    assertTrue(storedTags.contains("urgent"));
    assertTrue(storedTags.contains("project"));
  }

  @Test
  public void mentionFieldIsStoredInDocument() {
    WaveDocumentBuilder builder = newBuilder();
    WaveId waveId = WaveId.of("example.com", "w+mention");
    Document doc = builder.build(metadataWithMentions(waveId, "alice@example.com"), null);

    IndexableField[] mentionFields = doc.getFields(Lucene9FieldNames.MENTIONED);
    assertEquals(1, mentionFields.length);
    assertEquals("alice@example.com", mentionFields[0].stringValue());
  }

  @Test
  public void emptyMentionsAreNotIndexed() {
    WaveDocumentBuilder builder = newBuilder();
    WaveId waveId = WaveId.of("example.com", "w+emptymention");
    // Empty string should be skipped; only "bob@example.com" should survive.
    Document doc = builder.build(metadataWithMentions(waveId, "", "bob@example.com"), null);

    IndexableField[] mentionFields = doc.getFields(Lucene9FieldNames.MENTIONED);
    assertEquals(1, mentionFields.length);
    assertEquals("bob@example.com", mentionFields[0].stringValue());
  }

  // --- In-memory Lucene round-trip tests ---

  @Test
  public void tagTermQueryFindsIndexedWave() throws Exception {
    WaveDocumentBuilder builder = newBuilder();
    WaveId waveId = WaveId.of("example.com", "w+tagquery");
    Document doc = builder.build(metadataWithTags(waveId, "urgent"), null);

    try (ByteBuffersDirectory dir = new ByteBuffersDirectory();
        IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
      writer.addDocument(doc);
      writer.commit();
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs results = searcher.search(
            new TermQuery(new Term(Lucene9FieldNames.TAG, "urgent")), 10);
        assertEquals("tag:urgent should find 1 wave", 1L, results.totalHits.value);
        assertEquals(waveId.serialise(),
            searcher.storedFields().document(results.scoreDocs[0].doc)
                .get(Lucene9FieldNames.WAVE_ID));
      }
    }
  }

  @Test
  public void tagQueryDoesNotFindWaveWithDifferentTag() throws Exception {
    WaveDocumentBuilder builder = newBuilder();
    WaveId waveId = WaveId.of("example.com", "w+tagmiss");
    Document doc = builder.build(metadataWithTags(waveId, "urgent"), null);

    try (ByteBuffersDirectory dir = new ByteBuffersDirectory();
        IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
      writer.addDocument(doc);
      writer.commit();
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs results = searcher.search(
            new TermQuery(new Term(Lucene9FieldNames.TAG, "other")), 10);
        assertEquals("tag:other should not match a wave tagged 'urgent'",
            0L, results.totalHits.value);
      }
    }
  }

  @Test
  public void mentionTermQueryFindsIndexedWave() throws Exception {
    WaveDocumentBuilder builder = newBuilder();
    WaveId waveId = WaveId.of("example.com", "w+mentionquery");
    Document doc = builder.build(metadataWithMentions(waveId, "alice@example.com"), null);

    try (ByteBuffersDirectory dir = new ByteBuffersDirectory();
        IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
      writer.addDocument(doc);
      writer.commit();
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs results = searcher.search(
            new TermQuery(new Term(Lucene9FieldNames.MENTIONED, "alice@example.com")), 10);
        assertEquals("MENTIONED field should find wave mentioning alice",
            1L, results.totalHits.value);
      }
    }
  }

  @Test
  public void taskAssigneeFieldIsStoredInDocument() {
    WaveDocumentBuilder builder = newBuilder();
    WaveId waveId = WaveId.of("example.com", "w+task");
    Document doc = builder.build(
        metadataWithTaskAssignees(waveId, "alice@example.com", "bob@example.com"), null);

    IndexableField[] fields = doc.getFields(Lucene9FieldNames.TASK_ASSIGNEE);
    assertEquals(2, fields.length);
    Set<String> storedAssignees = new HashSet<>();
    for (IndexableField f : fields) storedAssignees.add(f.stringValue());
    assertTrue(storedAssignees.contains("alice@example.com"));
    assertTrue(storedAssignees.contains("bob@example.com"));
  }

  @Test
  public void emptyTaskAssigneesAreNotIndexed() {
    WaveDocumentBuilder builder = newBuilder();
    WaveId waveId = WaveId.of("example.com", "w+emptytask");
    Document doc = builder.build(
        metadataWithTaskAssignees(waveId, "", "bob@example.com"), null);

    IndexableField[] fields = doc.getFields(Lucene9FieldNames.TASK_ASSIGNEE);
    assertEquals(1, fields.length);
    assertEquals("bob@example.com", fields[0].stringValue());
  }

  @Test
  public void taskAssigneeTermQueryFindsIndexedWave() throws Exception {
    WaveDocumentBuilder builder = newBuilder();
    WaveId waveId = WaveId.of("example.com", "w+taskquery");
    Document doc = builder.build(
        metadataWithTaskAssignees(waveId, "alice@example.com"), null);

    try (ByteBuffersDirectory dir = new ByteBuffersDirectory();
        IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
      writer.addDocument(doc);
      writer.commit();
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs results = searcher.search(
            new TermQuery(new Term(Lucene9FieldNames.TASK_ASSIGNEE, "alice@example.com")), 10);
        assertEquals("TASK_ASSIGNEE field should find wave with task assigned to alice",
            1L, results.totalHits.value);
      }
    }
  }

  @Test
  public void reindexUpdatesTagInExistingDocument() throws Exception {
    // Simulates the real-time re-index path: a wave is first indexed without a tag,
    // then re-indexed via updateDocument() with a tag — the tag must be searchable after.
    WaveDocumentBuilder builder = newBuilder();
    WaveId waveId = WaveId.of("example.com", "w+reindex");

    try (ByteBuffersDirectory dir = new ByteBuffersDirectory();
        IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
      // First index: no tag
      Document docV1 = builder.build(metadataWithTags(waveId /* no tags */), null);
      writer.updateDocument(new Term(Lucene9FieldNames.DOC_ID, waveId.serialise()), docV1);
      writer.commit();

      // Re-index: wave now has a tag
      Document docV2 = builder.build(metadataWithTags(waveId, "meeting"), null);
      writer.updateDocument(new Term(Lucene9FieldNames.DOC_ID, waveId.serialise()), docV2);
      writer.commit();

      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals("reindex must replace existing doc, not insert duplicate",
            1, reader.numDocs());
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs results = searcher.search(
            new TermQuery(new Term(Lucene9FieldNames.TAG, "meeting")), 10);
        assertEquals("tag added after initial indexing must be searchable after re-index",
            1L, results.totalHits.value);
      }
    }
  }
}
