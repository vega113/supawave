package org.waveprotocol.box.j2cl.transport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class SidecarFragmentsResponse {
  public static final class BlipMetadata {
    private final String id;
    private final String author;
    private final long lastModifiedTime;

    BlipMetadata(String id, String author, long lastModifiedTime) {
      this.id = id == null ? "" : id;
      this.author = author == null ? "" : author;
      this.lastModifiedTime = Math.max(0L, lastModifiedTime);
    }

    public String getId() {
      return id;
    }

    public String getAuthor() {
      return author;
    }

    public long getLastModifiedTime() {
      return lastModifiedTime;
    }
  }

  private final String status;
  private final String waveRefPath;
  private final SidecarSelectedWaveFragments fragments;
  private final List<BlipMetadata> blips;

  private SidecarFragmentsResponse(
      String status,
      String waveRefPath,
      SidecarSelectedWaveFragments fragments,
      List<BlipMetadata> blips) {
    this.status = status == null ? "" : status;
    this.waveRefPath = waveRefPath == null ? "" : waveRefPath;
    this.fragments = fragments == null
        ? new SidecarSelectedWaveFragments(
            -1L,
            0L,
            0L,
            Collections.<SidecarSelectedWaveFragmentRange>emptyList(),
            Collections.<SidecarSelectedWaveFragment>emptyList())
        : fragments;
    this.blips =
        blips == null
            ? Collections.<BlipMetadata>emptyList()
            : Collections.unmodifiableList(new ArrayList<BlipMetadata>(blips));
  }

  public static SidecarFragmentsResponse fromJson(String json) {
    Map<String, Object> root = SidecarTransportCodec.parseJsonObject(json);
    String status = getString(root, "status");
    if (!"ok".equals(status)) {
      throw new IllegalArgumentException("Unexpected fragments response status: " + status);
    }
    Map<String, Object> version = getObject(root.get("version"));
    List<SidecarSelectedWaveFragmentRange> ranges =
        new ArrayList<SidecarSelectedWaveFragmentRange>();
    Object rawRanges = root.get("ranges");
    if (rawRanges != null) {
      for (Object rawRange : asList(rawRanges)) {
        Map<String, Object> range = getObject(rawRange);
        ranges.add(
            new SidecarSelectedWaveFragmentRange(
                getRequiredString(range, "segment"),
                getLong(range, "from"),
                getLong(range, "to")));
      }
    }

    List<SidecarSelectedWaveFragment> entries =
        new ArrayList<SidecarSelectedWaveFragment>();
    Object rawFragments = root.get("fragments");
    if (rawFragments != null) {
      for (Object rawFragment : asList(rawFragments)) {
        Map<String, Object> fragment = getObject(rawFragment);
        String rawSnapshot = getNullableString(fragment, "rawSnapshot");
        int bodyItemCount =
            fragment.containsKey("bodyItemCount")
                ? (int) getOptionalLong(fragment, "bodyItemCount")
                : SidecarSelectedWaveFragment.estimateBodyItemCount(rawSnapshot);
        entries.add(
            new SidecarSelectedWaveFragment(
                getRequiredString(fragment, "segment"),
                rawSnapshot,
                bodyItemCount,
                // TODO(#967 Task 5): decode operation bodies when growth windows apply deltas.
                getArrayLength(fragment.get("adjust")),
                getArrayLength(fragment.get("diff"))));
      }
    }

    List<BlipMetadata> blips = new ArrayList<BlipMetadata>();
    Object rawBlips = root.get("blips");
    if (rawBlips != null) {
      for (Object rawBlip : asList(rawBlips)) {
        Map<String, Object> blip = getObject(rawBlip);
        blips.add(
            new BlipMetadata(
                getRequiredString(blip, "id"),
                getString(blip, "author"),
                getOptionalLong(blip, "lastModifiedTime")));
      }
    }

    return new SidecarFragmentsResponse(
        status,
        getString(root, "waveRef"),
        new SidecarSelectedWaveFragments(
            getLong(version, "snapshot"),
            getLong(version, "start"),
            getLong(version, "end"),
            ranges,
            entries),
        blips);
  }

  public String getStatus() {
    return status;
  }

  public String getWaveRefPath() {
    return waveRefPath;
  }

  public SidecarSelectedWaveFragments getFragments() {
    return fragments;
  }

  public List<BlipMetadata> getBlips() {
    return blips;
  }

  private static Map<String, Object> getObject(Object value) {
    if (!(value instanceof Map)) {
      throw new IllegalArgumentException("Expected object but got " + value);
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> object = (Map<String, Object>) value;
    return object;
  }

  private static List<Object> asList(Object value) {
    if (!(value instanceof List)) {
      throw new IllegalArgumentException("Expected array but got " + value);
    }
    @SuppressWarnings("unchecked")
    List<Object> list = (List<Object>) value;
    return list;
  }

  private static String getString(Map<String, Object> object, String key) {
    Object value = object.get(key);
    return value == null ? "" : String.valueOf(value);
  }

  private static String getRequiredString(Map<String, Object> object, String key) {
    Object value = object.get(key);
    if (value == null) {
      throw new IllegalArgumentException("Missing required field: " + key);
    }
    return String.valueOf(value);
  }

  private static String getNullableString(Map<String, Object> object, String key) {
    if (!object.containsKey(key)) {
      return null;
    }
    Object value = object.get(key);
    return value == null ? null : String.valueOf(value);
  }

  private static long getLong(Map<String, Object> object, String key) {
    Object value = object.get(key);
    if (!(value instanceof Number)) {
      throw new IllegalArgumentException("Expected numeric '" + key + "' but got " + value);
    }
    return ((Number) value).longValue();
  }

  private static long getOptionalLong(Map<String, Object> object, String key) {
    Object value = object.get(key);
    if (value == null) {
      return 0L;
    }
    if (!(value instanceof Number)) {
      throw new IllegalArgumentException("Expected numeric '" + key + "' but got " + value);
    }
    return ((Number) value).longValue();
  }

  private static int getArrayLength(Object value) {
    return value instanceof List ? ((List<?>) value).size() : 0;
  }
}
