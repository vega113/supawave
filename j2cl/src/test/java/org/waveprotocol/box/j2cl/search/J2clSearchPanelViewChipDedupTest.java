package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import org.junit.Assert;
import org.junit.Test;

/**
 * J-UI-2 (#1080 / R-4.5): unit tests for the chip-driven submit dedupe
 * predicate in {@link J2clSearchPanelView#shouldSuppressChipDrivenSubmit}.
 *
 * <p>The Lit rail emits both {@code wavy-search-filter-toggled} and
 * {@code wavy-search-submit} for a single chip click. The view binds
 * both events; the filter-toggled handler pre-arms a flag with the
 * composed query, and the submit handler consults the flag before
 * issuing a duplicate backend search. This test pins down the
 * predicate's behavior so a future refactor cannot silently introduce
 * a duplicate-search regression.
 */
@J2clTestInput(J2clSearchPanelViewChipDedupTest.class)
public class J2clSearchPanelViewChipDedupTest {
  @Test
  public void noPendingMeansForwardSubmit() {
    Assert.assertFalse(
        J2clSearchPanelView.shouldSuppressChipDrivenSubmit(null, "in:inbox"));
  }

  @Test
  public void matchingPendingSuppressesSubmit() {
    Assert.assertTrue(
        J2clSearchPanelView.shouldSuppressChipDrivenSubmit(
            "in:inbox is:unread", "in:inbox is:unread"));
  }

  @Test
  public void mismatchedPendingForwardsSubmit() {
    // The user typed a new query while a chip-driven submit was
    // pending — that submit must NOT suppress the user's typed query.
    Assert.assertFalse(
        J2clSearchPanelView.shouldSuppressChipDrivenSubmit(
            "in:inbox is:unread", "title:meeting"));
  }

  @Test
  public void emptyPendingNeverMatchesNonEmptyResolved() {
    Assert.assertFalse(
        J2clSearchPanelView.shouldSuppressChipDrivenSubmit("", "in:inbox"));
  }

  @Test
  public void caseSensitiveMatchOnly() {
    // The Lit rail's chip composition normalises whitespace but does
    // NOT case-fold tokens — the dedupe predicate must therefore be
    // case-sensitive so a user-typed `IS:UNREAD` does not get
    // suppressed by a chip-driven `is:unread`.
    Assert.assertFalse(
        J2clSearchPanelView.shouldSuppressChipDrivenSubmit(
            "in:inbox is:unread", "IN:INBOX IS:UNREAD"));
  }
}
