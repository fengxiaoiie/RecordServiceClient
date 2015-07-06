// Confidential Cloudera Information: Covered by NDA.
// Copyright 2012 Cloudera Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.cloudera.recordservice.avro.mapreduce;

import java.io.IOException;

import org.apache.avro.Schema;
import org.apache.avro.mapred.AvroKey;
import org.apache.avro.mapreduce.AvroJob;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cloudera.recordservice.avro.SpecificRecords;
import com.cloudera.recordservice.avro.SpecificRecords.ResolveBy;
import com.cloudera.recordservice.mapreduce.RecordServiceInputFormatBase;
import com.cloudera.recordservice.thrift.TRecordServiceException;

/**
 * Input format which provides identical functionality to
 * org.apache.mapreduce.AvroKeyInputFormat
 */
public class AvroKeyInputFormat<T> extends
    RecordServiceInputFormatBase<AvroKey<T>, NullWritable> {

  private static final Logger LOG = LoggerFactory.getLogger(AvroKeyInputFormat.class);

  @Override
  public RecordReader<AvroKey<T>, NullWritable> createRecordReader(
      InputSplit split, TaskAttemptContext context)
      throws IOException, InterruptedException {
    Schema readerSchema = AvroJob.getInputKeySchema(context.getConfiguration());
    if (null == readerSchema) {
      // FIXME: handle this. The writer schema is just the HMS schema for us.
      LOG.warn("Reader schema was not set. Use AvroJob.setInputKeySchema() if desired.");
      LOG.info("Using a reader schema equal to the writer schema.");
    }
    return new AvroKeyRecordReader<T>(readerSchema);
  }

  private static class AvroKeyRecordReader<T>
      extends RecordReaderBase<AvroKey<T>, NullWritable> {
    // The schema of the returned records.
    private final Schema avroSchema_;

    // A reusable object to hold the current record.
    private final AvroKey<T> mCurrentRecord;

    // Records to return.
    private SpecificRecords<T> records_;

    /**
     * Constructor.
     */
    public AvroKeyRecordReader(Schema schema) {
      avroSchema_ = schema;
      mCurrentRecord = new AvroKey<T>(null);
    }

    /** {@inheritDoc} */
    @Override
    public boolean nextKeyValue() throws IOException, InterruptedException {
      try {
        if (!records_.hasNext()) return false;
        mCurrentRecord.datum(records_.next());
        return true;
      } catch (TRecordServiceException e) {
        throw new IOException("Could not fetch record.", e);
      }
    }

    /** {@inheritDoc} */
    @Override
    public AvroKey<T> getCurrentKey() throws IOException, InterruptedException {
      return mCurrentRecord;
    }

    /** {@inheritDoc} */
    @Override
    public NullWritable getCurrentValue() throws IOException, InterruptedException {
      return NullWritable.get();
    }

    @Override
    public void initialize(InputSplit inputSplit, TaskAttemptContext context)
        throws IOException, InterruptedException {
      super.initialize(inputSplit, context);
      records_ = new SpecificRecords<T>(
          avroSchema_, reader_.records(), ResolveBy.NAME);
    }
  }
}