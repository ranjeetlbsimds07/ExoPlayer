/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.source.dash.manifest;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.dash.DashSegmentIndex;
import com.google.android.exoplayer2.source.dash.manifest.SegmentBase.MultiSegmentBase;
import com.google.android.exoplayer2.source.dash.manifest.SegmentBase.SingleSegmentBase;

import android.net.Uri;

/**
 * A DASH representation.
 */
public abstract class Representation {

  /**
   * Identifies the piece of content to which this {@link Representation} belongs.
   * <p>
   * For example, all {@link Representation}s belonging to a video should have the same content
   * identifier that uniquely identifies that video.
   */
  public final String contentId;
  /**
   * Identifies the revision of the content.
   * <p>
   * If the media for a given ({@link #contentId} can change over time without a change to the
   * {@link #format}'s {@link Format#id} (e.g. as a result of re-encoding the media with an
   * updated encoder), then this identifier must uniquely identify the revision of the media. The
   * timestamp at which the media was encoded is often a suitable.
   */
  public final long revisionId;
  /**
   * The format of the representation.
   */
  public final Format format;
  /**
   * The offset of the presentation timestamps in the media stream relative to media time.
   */
  public final long presentationTimeOffsetUs;

  private final String cacheKey;
  private final RangedUri initializationUri;

  /**
   * Constructs a new instance.
   *
   * @param contentId Identifies the piece of content to which this representation belongs.
   * @param revisionId Identifies the revision of the content.
   * @param format The format of the representation.
   * @param segmentBase A segment base element for the representation.
   * @return The constructed instance.
   */
  public static Representation newInstance(String contentId, long revisionId, Format format,
      SegmentBase segmentBase) {
    return newInstance(contentId, revisionId, format, segmentBase, null);
  }

  /**
   * Constructs a new instance.
   *
   * @param contentId Identifies the piece of content to which this representation belongs.
   * @param revisionId Identifies the revision of the content.
   * @param format The format of the representation.
   * @param segmentBase A segment base element for the representation.
   * @param customCacheKey A custom value to be returned from {@link #getCacheKey()}, or null.
   * @return The constructed instance.
   */
  public static Representation newInstance(String contentId, long revisionId, Format format,
      SegmentBase segmentBase, String customCacheKey) {
    if (segmentBase instanceof SingleSegmentBase) {
      return new SingleSegmentRepresentation(contentId, revisionId, format,
          (SingleSegmentBase) segmentBase, customCacheKey, -1);
    } else if (segmentBase instanceof MultiSegmentBase) {
      return new MultiSegmentRepresentation(contentId, revisionId, format,
          (MultiSegmentBase) segmentBase, customCacheKey);
    } else {
      throw new IllegalArgumentException("segmentBase must be of type SingleSegmentBase or "
          + "MultiSegmentBase");
    }
  }

  private Representation(String contentId, long revisionId, Format format,
      SegmentBase segmentBase, String customCacheKey) {
    this.contentId = contentId;
    this.revisionId = revisionId;
    this.format = format;
    this.cacheKey = customCacheKey != null ? customCacheKey
        : contentId + "." + format.id + "." + revisionId;
    initializationUri = segmentBase.getInitialization(this);
    presentationTimeOffsetUs = segmentBase.getPresentationTimeOffsetUs();
  }

  /**
   * Returns a {@link RangedUri} defining the location of the representation's initialization data,
   * or null if no initialization data exists.
   */
  public RangedUri getInitializationUri() {
    return initializationUri;
  }

  /**
   * Returns a {@link RangedUri} defining the location of the representation's segment index, or
   * null if the representation provides an index directly.
   */
  public abstract RangedUri getIndexUri();

  /**
   * Returns an index if the representation provides one directly, or null otherwise.
   */
  public abstract DashSegmentIndex getIndex();

  /**
   * Returns a cache key for the representation, in the format
   * {@code contentId + "." + format.id + "." + revisionId}.
   */
  public String getCacheKey() {
    return cacheKey;
  }

  /**
   * A DASH representation consisting of a single segment.
   */
  public static class SingleSegmentRepresentation extends Representation {

    /**
     * The uri of the single segment.
     */
    public final Uri uri;

    /**
     * The content length, or -1 if unknown.
     */
    public final long contentLength;

    private final RangedUri indexUri;
    private final SingleSegmentIndex segmentIndex;

    /**
     * @param contentId Identifies the piece of content to which this representation belongs.
     * @param revisionId Identifies the revision of the content.
     * @param format The format of the representation.
     * @param uri The uri of the media.
     * @param initializationStart The offset of the first byte of initialization data.
     * @param initializationEnd The offset of the last byte of initialization data.
     * @param indexStart The offset of the first byte of index data.
     * @param indexEnd The offset of the last byte of index data.
     * @param customCacheKey A custom value to be returned from {@link #getCacheKey()}, or null.
     * @param contentLength The content length, or -1 if unknown.
     */
    public static SingleSegmentRepresentation newInstance(String contentId, long revisionId,
        Format format, String uri, long initializationStart, long initializationEnd,
        long indexStart, long indexEnd, String customCacheKey, long contentLength) {
      RangedUri rangedUri = new RangedUri(uri, null, initializationStart,
          initializationEnd - initializationStart + 1);
      SingleSegmentBase segmentBase = new SingleSegmentBase(rangedUri, 1, 0, uri, indexStart,
          indexEnd - indexStart + 1);
      return new SingleSegmentRepresentation(contentId, revisionId,
          format, segmentBase, customCacheKey, contentLength);
    }

    /**
     * @param contentId Identifies the piece of content to which this representation belongs.
     * @param revisionId Identifies the revision of the content.
     * @param format The format of the representation.
     * @param segmentBase The segment base underlying the representation.
     * @param customCacheKey A custom value to be returned from {@link #getCacheKey()}, or null.
     * @param contentLength The content length, or -1 if unknown.
     */
    public SingleSegmentRepresentation(String contentId, long revisionId, Format format,
        SingleSegmentBase segmentBase, String customCacheKey, long contentLength) {
      super(contentId, revisionId, format, segmentBase, customCacheKey);
      this.uri = Uri.parse(segmentBase.uri);
      this.indexUri = segmentBase.getIndex();
      this.contentLength = contentLength;
      // If we have an index uri then the index is defined externally, and we shouldn't return one
      // directly. If we don't, then we can't do better than an index defining a single segment.
      segmentIndex = indexUri != null ? null
          : new SingleSegmentIndex(new RangedUri(segmentBase.uri, null, 0, contentLength));
    }

    @Override
    public RangedUri getIndexUri() {
      return indexUri;
    }

    @Override
    public DashSegmentIndex getIndex() {
      return segmentIndex;
    }

  }

  /**
   * A DASH representation consisting of multiple segments.
   */
  public static class MultiSegmentRepresentation extends Representation
      implements DashSegmentIndex {

    private final MultiSegmentBase segmentBase;

    /**
     * @param contentId Identifies the piece of content to which this representation belongs.
     * @param revisionId Identifies the revision of the content.
     * @param format The format of the representation.
     * @param segmentBase The segment base underlying the representation.
     * @param customCacheKey A custom value to be returned from {@link #getCacheKey()}, or null.
     */
    public MultiSegmentRepresentation(String contentId, long revisionId, Format format,
        MultiSegmentBase segmentBase, String customCacheKey) {
      super(contentId, revisionId, format, segmentBase, customCacheKey);
      this.segmentBase = segmentBase;
    }

    @Override
    public RangedUri getIndexUri() {
      return null;
    }

    @Override
    public DashSegmentIndex getIndex() {
      return this;
    }

    // DashSegmentIndex implementation.

    @Override
    public RangedUri getSegmentUrl(int segmentIndex) {
      return segmentBase.getSegmentUrl(this, segmentIndex);
    }

    @Override
    public int getSegmentNum(long timeUs, long periodDurationUs) {
      return segmentBase.getSegmentNum(timeUs, periodDurationUs);
    }

    @Override
    public long getTimeUs(int segmentIndex) {
      return segmentBase.getSegmentTimeUs(segmentIndex);
    }

    @Override
    public long getDurationUs(int segmentIndex, long periodDurationUs) {
      return segmentBase.getSegmentDurationUs(segmentIndex, periodDurationUs);
    }

    @Override
    public int getFirstSegmentNum() {
      return segmentBase.getFirstSegmentNum();
    }

    @Override
    public int getLastSegmentNum(long periodDurationUs) {
      return segmentBase.getLastSegmentNum(periodDurationUs);
    }

    @Override
    public boolean isExplicit() {
      return segmentBase.isExplicit();
    }

  }

}