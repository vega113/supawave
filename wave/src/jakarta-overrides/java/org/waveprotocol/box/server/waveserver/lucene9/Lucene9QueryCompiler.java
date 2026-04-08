/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.waveprotocol.box.server.waveserver.lucene9;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.index.Term;
import org.waveprotocol.box.server.waveserver.QueryHelper.InvalidQueryException;
import org.waveprotocol.box.server.waveserver.QueryHelper.OrderByValueType;
import org.waveprotocol.box.server.waveserver.TokenQueryType;
import org.waveprotocol.box.server.waveserver.TaskQueryNormalizer;
import org.waveprotocol.wave.model.wave.InvalidParticipantAddress;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.ParticipantIdUtil;

@Singleton
public class Lucene9QueryCompiler {

  private final StandardAnalyzer analyzer = new StandardAnalyzer();

  @Inject
  public Lucene9QueryCompiler() {
  }

  public Query compile(Lucene9QueryModel model, ParticipantId user) throws InvalidQueryException {
    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    builder.add(accessQuery(model, user), Occur.MUST);
    for (ParticipantId participant : normalizeParticipants(model.values(TokenQueryType.WITH),
        user.getDomain())) {
      builder.add(new TermQuery(new Term(Lucene9FieldNames.PARTICIPANT,
          participant.getAddress())), Occur.MUST);
    }
    addCreatorQuery(builder, model.values(TokenQueryType.CREATOR), user.getDomain());
    for (String tag : model.values(TokenQueryType.TAG)) {
      builder.add(new TermQuery(new Term(Lucene9FieldNames.TAG,
          tag.toLowerCase(Locale.ROOT))), Occur.MUST);
    }
    addTextQueries(builder, Lucene9FieldNames.TITLE_TEXT, model.values(TokenQueryType.TITLE));
    addTextQueries(builder, Lucene9FieldNames.CONTENT_TEXT, model.values(TokenQueryType.CONTENT));
    for (String taskAssignee : normalizeTaskAssignees(model.values(TokenQueryType.TASKS), user)) {
      builder.add(new TermQuery(new Term(Lucene9FieldNames.TASK_ASSIGNEE, taskAssignee)),
          Occur.MUST);
    }
    return builder.build();
  }

  public Sort compileSort(Lucene9QueryModel model) throws InvalidQueryException {
    List<String> orderByValues = model.values(TokenQueryType.ORDERBY);
    if (orderByValues.isEmpty()) {
      return new Sort(new SortField(Lucene9FieldNames.LAST_MODIFIED_SORT,
          SortField.Type.LONG, true));
    }
    SortField[] sortFields = new SortField[orderByValues.size()];
    for (int i = 0; i < orderByValues.size(); i++) {
      try {
        sortFields[i] = toSortField(OrderByValueType.fromToken(orderByValues.get(i)));
      } catch (IllegalArgumentException e) {
        throw new InvalidQueryException(e.getMessage());
      }
    }
    return new Sort(sortFields);
  }

  private Query accessQuery(Lucene9QueryModel model, ParticipantId user) {
    List<String> inValues = model.values(TokenQueryType.IN);
    boolean hasPersonalFolder = inValues.contains("inbox") || inValues.contains("archive");
    boolean hasSharedFolder = inValues.contains("all") || inValues.contains("pinned");
    boolean explicitOnly = hasPersonalFolder && !hasSharedFolder;
    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    builder.add(new TermQuery(new Term(Lucene9FieldNames.PARTICIPANT,
        user.getAddress())), Occur.SHOULD);
    if (!explicitOnly) {
      builder.add(new TermQuery(new Term(Lucene9FieldNames.PARTICIPANT,
          ParticipantIdUtil.makeUnsafeSharedDomainParticipantId(user.getDomain()).getAddress())),
          Occur.SHOULD);
    }
    builder.setMinimumNumberShouldMatch(1);
    return builder.build();
  }

  private void addCreatorQuery(BooleanQuery.Builder builder, List<String> creators,
      String localDomain) throws InvalidQueryException {
    if (creators.isEmpty()) {
      return;
    }
    Set<String> distinctCreators = new LinkedHashSet<>();
    for (ParticipantId creator : normalizeParticipants(creators, localDomain)) {
      distinctCreators.add(creator.getAddress());
    }
    if (distinctCreators.size() > 1) {
      builder.add(new MatchNoDocsQuery("creator filters must match the same wavelet"),
          Occur.MUST);
      return;
    }
    builder.add(new TermQuery(new Term(Lucene9FieldNames.CREATOR_FILTER,
        distinctCreators.iterator().next())), Occur.MUST);
  }

  private void addTextQueries(BooleanQuery.Builder builder, String fieldName, List<String> values)
      throws InvalidQueryException {
    for (String value : values) {
      QueryParser parser = new QueryParser(fieldName, analyzer);
      try {
        String escapedValue = QueryParser.escape(value);
        String expression = value.indexOf(' ') >= 0 ? "\"" + escapedValue + "\"" : escapedValue;
        builder.add(parser.parse(expression), Occur.MUST);
      } catch (ParseException e) {
        throw new InvalidQueryException(e.getMessage());
      }
    }
  }

  private SortField toSortField(OrderByValueType orderByValue) {
    switch (orderByValue) {
      case DATEASC:
        return new SortField(Lucene9FieldNames.LAST_MODIFIED_SORT, SortField.Type.LONG, false);
      case DATEDESC:
        return new SortField(Lucene9FieldNames.LAST_MODIFIED_SORT, SortField.Type.LONG, true);
      case CREATEDASC:
        return new SortField(Lucene9FieldNames.CREATED_SORT, SortField.Type.LONG, false);
      case CREATEDDESC:
        return new SortField(Lucene9FieldNames.CREATED_SORT, SortField.Type.LONG, true);
      case CREATORASC:
        return new SortField(Lucene9FieldNames.CREATOR_SORT, SortField.Type.STRING, false);
      case CREATORDESC:
        return new SortField(Lucene9FieldNames.CREATOR_SORT, SortField.Type.STRING, true);
      default:
        return new SortField(Lucene9FieldNames.LAST_MODIFIED_SORT, SortField.Type.LONG, true);
    }
  }

  private List<String> normalizeTaskAssignees(List<String> rawValues, ParticipantId user)
      throws InvalidQueryException {
    List<String> normalized = new java.util.ArrayList<>();
    for (String raw : rawValues) {
      normalized.add(TaskQueryNormalizer.normalize(raw, user));
    }
    return normalized;
  }

  private List<ParticipantId> normalizeParticipants(List<String> rawValues, String localDomain)
      throws InvalidQueryException {
    List<ParticipantId> participants = new java.util.ArrayList<>();
    for (String rawValue : rawValues) {
      String normalized = rawValue;
      if ("@".equals(rawValue)) {
        participants.add(ParticipantIdUtil.makeUnsafeSharedDomainParticipantId(localDomain));
        continue;
      }
      if (!rawValue.contains("@")) {
        normalized = rawValue + "@" + localDomain;
      }
      try {
        participants.add(ParticipantId.of(normalized));
      } catch (InvalidParticipantAddress e) {
        throw new InvalidQueryException(e.getMessage());
      }
    }
    return participants;
  }

}
