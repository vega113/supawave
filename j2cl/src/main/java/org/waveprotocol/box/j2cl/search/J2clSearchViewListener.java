package org.waveprotocol.box.j2cl.search;

public interface J2clSearchViewListener {
  void onQuerySubmitted(String query);

  void onShowMoreRequested();

  void onDigestSelected(String waveId);
}
