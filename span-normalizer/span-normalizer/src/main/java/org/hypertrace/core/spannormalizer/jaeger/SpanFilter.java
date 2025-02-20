package org.hypertrace.core.spannormalizer.jaeger;

import com.google.common.util.concurrent.RateLimiter;
import com.typesafe.config.Config;
import io.jaegertracing.api_v2.JaegerSpanInternalModel;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.hypertrace.core.span.constants.RawSpanConstants;
import org.hypertrace.core.span.constants.v1.SpanAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("UnstableApiUsage")
public class SpanFilter {
  private static final Logger LOG = LoggerFactory.getLogger(SpanFilter.class);
  private static final RateLimiter DROPPED_SPANS_RATE_LIMITER = RateLimiter.create(0.01);
  private static final String SPAN_KIND_TAG =
      RawSpanConstants.getValue(SpanAttribute.SPAN_ATTRIBUTE_SPAN_KIND);
  private static final String SPAN_KIND_CLIENT = "client";
  /**
   * Config key using which a list of criterion can be specified to drop the matching spans. Any
   * span matching any one of the criterion is dropped. Each criteria is a comma separated list of
   * key:value pairs and multiple pairs in one criteria are AND'ed.
   *
   * <p>For example:
   * ["messaging.destination_kind:queue,messaging.operation:receive,messaging.system:jms"] drops all
   * spans which have all 3 attribute:value pairs.
   */
  private static final String SPAN_DROP_CRITERION_CONFIG = "processor.spanDropCriterion";

  public static final String ROOT_SPAN_DROP_CRITERION_CONFIG =
      "processor.rootExitSpanDropCriterion";
  private static final String ROOT_SPAN_ALWAYS_DROP = "alwaysDrop";
  private static final String ROOT_SPAN_DROP_EXCLUSIONS = "exclusionsMatchCriterion";

  private static final String COMMA = ",";
  private static final String COLON = ":";

  private List<List<Pair<String, String>>> spanDropCriterion = Collections.emptyList();
  private boolean alwaysDropRootSpan = false;
  private List<List<Pair<String, String>>> rootSpanDropExclusionCriterion = Collections.emptyList();

  public SpanFilter(Config config) {
    if (config.hasPath(SPAN_DROP_CRITERION_CONFIG)) {
      List<String> criterion = config.getStringList(SPAN_DROP_CRITERION_CONFIG);
      LOG.info("Span drop criterion: {}", criterion);
      // Parse the config to see if there is any criteria to drop spans.
      this.spanDropCriterion = parseStringList(criterion);
    }

    if (config.hasPath(ROOT_SPAN_DROP_CRITERION_CONFIG)) {
      Config rootSpanDropCriterionConfig = config.getConfig(ROOT_SPAN_DROP_CRITERION_CONFIG);
      LOG.info("Root Span drop criterion: {}", rootSpanDropCriterionConfig);
      this.alwaysDropRootSpan =
          rootSpanDropCriterionConfig.hasPath(ROOT_SPAN_ALWAYS_DROP)
              && rootSpanDropCriterionConfig.getBoolean(ROOT_SPAN_ALWAYS_DROP);
      List<String> exclusionList =
          rootSpanDropCriterionConfig.hasPath(ROOT_SPAN_DROP_EXCLUSIONS)
              ? rootSpanDropCriterionConfig.getStringList(ROOT_SPAN_DROP_EXCLUSIONS)
              : Collections.emptyList();
      // Parse the config to see if there is any criteria to drop spans.
      this.rootSpanDropExclusionCriterion = parseStringList(exclusionList);
    }
  }

  private List<List<Pair<String, String>>> parseStringList(List<String> stringList) {
    return stringList.stream()
        // Split each criteria based on comma
        .map(s -> s.split(COMMA))
        .map(
            a ->
                Arrays.stream(a)
                    .map(this::convertToPair)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()))
        .collect(Collectors.toList());
  }

  /**
   * Method to check if the given span attributes match any of the drop criterion. Returns true if
   * the span should be dropped, false otherwise.
   */
  public boolean shouldDropSpan(
      JaegerSpanInternalModel.Span span, Map<String, JaegerSpanInternalModel.KeyValue> tags) {
    if (anyCriteriaMatch(tags, spanDropCriterion)) {
      if (DROPPED_SPANS_RATE_LIMITER.tryAcquire()) {
        LOG.info("Dropping span: [{}] with drop criterion: [{}]", span, spanDropCriterion);
      }
      return true;
    }

    if (isRootExitSpan(span, tags)) {
      boolean anyCriteriaMatch = anyCriteriaMatch(tags, rootSpanDropExclusionCriterion);
      boolean shouldDropSpan =
          (alwaysDropRootSpan && !anyCriteriaMatch) || (!alwaysDropRootSpan && anyCriteriaMatch);
      if (shouldDropSpan && DROPPED_SPANS_RATE_LIMITER.tryAcquire()) {
        LOG.info(
            "Dropping root exit span: [{}] alwaysDropRootSpan: [{}] exclusionCriterion: [{}]",
            span,
            alwaysDropRootSpan,
            rootSpanDropExclusionCriterion);
      }
      return shouldDropSpan;
    }
    return false;
  }

  @Nullable
  private Pair<String, String> convertToPair(String s) {
    if (s != null && s.contains(COLON)) {
      String[] parts = s.split(COLON);
      if (parts.length == 2) {
        return Pair.of(parts[0], parts[1]);
      }
    }
    return null;
  }

  private boolean anyCriteriaMatch(
      Map<String, JaegerSpanInternalModel.KeyValue> tags,
      List<List<Pair<String, String>>> criteriaList) {
    return criteriaList.stream()
        .anyMatch(
            l ->
                l.stream()
                    .allMatch(
                        p ->
                            tags.containsKey(p.getLeft())
                                && StringUtils.equals(
                                    tags.get(p.getLeft()).getVStr(), p.getRight())));
  }

  private boolean isRootExitSpan(
      JaegerSpanInternalModel.Span span, Map<String, JaegerSpanInternalModel.KeyValue> tags) {
    if (!span.getReferencesList().isEmpty()) {
      return false;
    }
    JaegerSpanInternalModel.KeyValue spanKindKeyValue = tags.get(SPAN_KIND_TAG);
    if (null == spanKindKeyValue) {
      return false;
    }

    return SPAN_KIND_CLIENT.equals(spanKindKeyValue.getVStr());
  }
}
