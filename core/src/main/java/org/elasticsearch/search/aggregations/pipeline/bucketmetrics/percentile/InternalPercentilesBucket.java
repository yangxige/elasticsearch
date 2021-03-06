/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.search.aggregations.pipeline.bucketmetrics.percentile;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.search.DocValueFormat;
import org.elasticsearch.search.aggregations.InternalAggregation;
import org.elasticsearch.search.aggregations.metrics.InternalNumericMetricsAggregation;
import org.elasticsearch.search.aggregations.metrics.max.InternalMax;
import org.elasticsearch.search.aggregations.metrics.percentiles.InternalPercentile;
import org.elasticsearch.search.aggregations.metrics.percentiles.Percentile;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregator;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class InternalPercentilesBucket extends InternalNumericMetricsAggregation.MultiValue implements PercentilesBucket {
    private double[] percentiles;
    private double[] percents;

    public InternalPercentilesBucket(String name, double[] percents, double[] percentiles,
                                     DocValueFormat formatter, List<PipelineAggregator> pipelineAggregators,
                                     Map<String, Object> metaData) {
        super(name, pipelineAggregators, metaData);
        this.format = formatter;
        this.percentiles = percentiles;
        this.percents = percents;
    }

    /**
     * Read from a stream.
     */
    public InternalPercentilesBucket(StreamInput in) throws IOException {
        super(in);
        format = in.readNamedWriteable(DocValueFormat.class);
        percentiles = in.readDoubleArray();
        percents = in.readDoubleArray();
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeNamedWriteable(format);
        out.writeDoubleArray(percentiles);
        out.writeDoubleArray(percents);
    }

    @Override
    public String getWriteableName() {
        return PercentilesBucketPipelineAggregationBuilder.NAME;
    }

    @Override
    public double percentile(double percent) throws IllegalArgumentException {
        int index = Arrays.binarySearch(percents, percent);
        if (index < 0) {
            throw new IllegalArgumentException("Percent requested [" + String.valueOf(percent) + "] was not" +
                    " one of the computed percentiles.  Available keys are: " + Arrays.toString(percents));
        }
        return percentiles[index];
    }

    @Override
    public String percentileAsString(double percent) {
        return format.format(percentile(percent));
    }

    @Override
    public Iterator<Percentile> iterator() {
        return new Iter(percents, percentiles);
    }

    @Override
    public double value(String name) {
        return percentile(Double.parseDouble(name));
    }

    @Override
    public InternalMax doReduce(List<InternalAggregation> aggregations, ReduceContext reduceContext) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public XContentBuilder doXContentBody(XContentBuilder builder, Params params) throws IOException {
        builder.startObject("values");
        for (double percent : percents) {
            double value = percentile(percent);
            boolean hasValue = !(Double.isInfinite(value) || Double.isNaN(value));
            String key = String.valueOf(percent);
            builder.field(key, hasValue ? value : null);
            if (hasValue && format != DocValueFormat.RAW) {
                builder.field(key + "_as_string", percentileAsString(percent));
            }
        }
        builder.endObject();
        return builder;
    }

    public static class Iter implements Iterator<Percentile> {

        private final double[] percents;
        private final double[] percentiles;
        private int i;

        public Iter(double[] percents, double[] percentiles) {
            this.percents = percents;
            this.percentiles = percentiles;
            i = 0;
        }

        @Override
        public boolean hasNext() {
            return i < percents.length;
        }

        @Override
        public Percentile next() {
            final Percentile next = new InternalPercentile(percents[i], percentiles[i]);
            ++i;
            return next;
        }

        @Override
        public final void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
