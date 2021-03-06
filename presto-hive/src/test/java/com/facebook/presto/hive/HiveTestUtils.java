/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.hive;

import com.facebook.presto.block.BlockEncodingManager;
import com.facebook.presto.hive.authentication.NoHdfsAuthentication;
import com.facebook.presto.hive.orc.DwrfPageSourceFactory;
import com.facebook.presto.hive.orc.OrcPageSourceFactory;
import com.facebook.presto.hive.parquet.ParquetPageSourceFactory;
import com.facebook.presto.hive.parquet.ParquetRecordCursorProvider;
import com.facebook.presto.hive.rcfile.RcFilePageSourceFactory;
import com.facebook.presto.hive.s3.HiveS3Config;
import com.facebook.presto.hive.s3.PrestoS3ConfigurationUpdater;
import com.facebook.presto.metadata.FunctionRegistry;
import com.facebook.presto.spi.ColumnHandle;
import com.facebook.presto.spi.ConnectorSession;
import com.facebook.presto.spi.type.MapType;
import com.facebook.presto.spi.type.StandardTypes;
import com.facebook.presto.spi.type.Type;
import com.facebook.presto.spi.type.TypeSignatureParameter;
import com.facebook.presto.sql.analyzer.FeaturesConfig;
import com.facebook.presto.testing.TestingConnectorSession;
import com.facebook.presto.type.TypeRegistry;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.List;
import java.util.Set;

public final class HiveTestUtils
{
    private HiveTestUtils()
    {
    }

    public static final ConnectorSession SESSION = new TestingConnectorSession(
            new HiveSessionProperties(new HiveClientConfig()).getSessionProperties());

    public static final TypeRegistry TYPE_MANAGER = new TypeRegistry();

    static {
        // associate TYPE_MANAGER with a function registry
        new FunctionRegistry(TYPE_MANAGER, new BlockEncodingManager(TYPE_MANAGER), new FeaturesConfig());
    }

    public static final HdfsEnvironment HDFS_ENVIRONMENT = createTestHdfsEnvironment(new HiveClientConfig());

    public static Set<HivePageSourceFactory> getDefaultHiveDataStreamFactories(HiveClientConfig hiveClientConfig)
    {
        FileFormatDataSourceStats stats = new FileFormatDataSourceStats();
        HdfsEnvironment testHdfsEnvironment = createTestHdfsEnvironment(hiveClientConfig);
        return ImmutableSet.<HivePageSourceFactory>builder()
                .add(new RcFilePageSourceFactory(TYPE_MANAGER, testHdfsEnvironment, stats))
                .add(new OrcPageSourceFactory(TYPE_MANAGER, hiveClientConfig, testHdfsEnvironment, stats))
                .add(new DwrfPageSourceFactory(TYPE_MANAGER, testHdfsEnvironment, stats))
                .add(new ParquetPageSourceFactory(TYPE_MANAGER, hiveClientConfig, testHdfsEnvironment))
                .build();
    }

    public static Set<HiveRecordCursorProvider> getDefaultHiveRecordCursorProvider(HiveClientConfig hiveClientConfig)
    {
        HdfsEnvironment testHdfsEnvironment = createTestHdfsEnvironment(hiveClientConfig);
        return ImmutableSet.<HiveRecordCursorProvider>builder()
                .add(new ParquetRecordCursorProvider(hiveClientConfig, testHdfsEnvironment))
                .add(new GenericHiveRecordCursorProvider(testHdfsEnvironment))
                .build();
    }

    public static Set<HiveFileWriterFactory> getDefaultHiveFileWriterFactories(HiveClientConfig hiveClientConfig)
    {
        HdfsEnvironment testHdfsEnvironment = createTestHdfsEnvironment(hiveClientConfig);
        return ImmutableSet.<HiveFileWriterFactory>builder()
                .add(new RcFileFileWriterFactory(testHdfsEnvironment, TYPE_MANAGER, new NodeVersion("test_version"), hiveClientConfig, new FileFormatDataSourceStats()))
                .add(new OrcFileWriterFactory(testHdfsEnvironment, TYPE_MANAGER, new NodeVersion("test_version"), hiveClientConfig, new FileFormatDataSourceStats()))
                .build();
    }

    public static List<Type> getTypes(List<? extends ColumnHandle> columnHandles)
    {
        ImmutableList.Builder<Type> types = ImmutableList.builder();
        for (ColumnHandle columnHandle : columnHandles) {
            types.add(TYPE_MANAGER.getType(((HiveColumnHandle) columnHandle).getTypeSignature()));
        }
        return types.build();
    }

    public static HdfsEnvironment createTestHdfsEnvironment(HiveClientConfig config)
    {
        return createTestHdfsEnvironment(config, new PrestoS3ConfigurationUpdater(new HiveS3Config()));
    }

    public static HdfsEnvironment createTestHdfsEnvironment(HiveClientConfig hiveConfig, S3ConfigurationUpdater s3Config)
    {
        HdfsConfiguration hdfsConfig = new HiveHdfsConfiguration(new HdfsConfigurationUpdater(hiveConfig, s3Config));
        return new HdfsEnvironment(hdfsConfig, hiveConfig, new NoHdfsAuthentication());
    }

    public static MapType mapType(Type keyType, Type valueType)
    {
        return (MapType) TYPE_MANAGER.getParameterizedType(StandardTypes.MAP, ImmutableList.of(
                TypeSignatureParameter.of(keyType.getTypeSignature()),
                TypeSignatureParameter.of(valueType.getTypeSignature())));
    }
}
