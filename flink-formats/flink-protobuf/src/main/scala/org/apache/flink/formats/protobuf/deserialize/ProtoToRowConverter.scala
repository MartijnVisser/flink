/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.flink.formats.protobuf.deserialize

import com.google.protobuf.ByteString
import com.google.protobuf.Descriptors.FileDescriptor.Syntax
import org.apache.flink.formats.protobuf._
import org.apache.flink.table.data._
import org.apache.flink.table.data.binary.BinaryStringData
import org.apache.flink.table.types.logical.RowType
import org.slf4j.LoggerFactory

import java.lang.reflect.Method
import java.util.UUID

/**
 * {@link ProtoToRowConverter} can convert binary protobuf message data to flink row data by codegen
 * process.
 */
object ProtoToRowConverter {
  private val LOG = LoggerFactory.getLogger(classOf[ProtoToRowConverter])
}


class ProtoToRowConverter @throws[PbCodegenException]
(val rowType: RowType, var formatConfig: PbFormatConfig) {

  final private var parseFromMethod: Method = null
  final private var decodeMethod: Method = null

  try {
    val outerPrefix = PbFormatUtils.getOuterProtoPrefix(formatConfig.getMessageClassName)
    val descriptor = PbFormatUtils.getDescriptor(formatConfig.getMessageClassName)
    val messageClass = Class.forName(formatConfig.getMessageClassName)
    val fullMessageClassName = PbFormatUtils.getFullJavaName(descriptor, outerPrefix)
    if (descriptor.getFile.getSyntax == Syntax.PROTO3) { // pb3 always read default values
      formatConfig = new PbFormatConfig(
        formatConfig.getMessageClassName,
        formatConfig.isIgnoreParseErrors,
        true,
        formatConfig.getWriteNullStringLiterals)
    }
    val pbFormatContext = new PbFormatContext(outerPrefix, formatConfig)
    val uuid = UUID.randomUUID.toString.replaceAll("\\-", "")
    val generatedClassName = "GeneratedProtoToRow_" + uuid
    val generatedPackageName = classOf[ProtoToRowConverter].getPackage.getName
    val codegenDes = PbCodegenDeserializeFactory.getPbCodegenTopRowDes(
      descriptor, rowType, pbFormatContext)
    val code =
      s"""
         | package ${generatedPackageName};
         | import ${classOf[RowData].getName};
         | import ${classOf[ArrayData].getName};
         | import ${classOf[BinaryStringData].getName};
         | import ${classOf[GenericRowData].getName};
         | import ${classOf[GenericMapData].getName};
         | import ${classOf[GenericArrayData].getName};
         | import ${classOf[java.util.ArrayList[_]].getName};
         | import ${classOf[java.util.List[_]].getName};
         | import ${classOf[java.util.Map[_, _]].getName};
         | import ${classOf[java.util.HashMap[_, _]].getName};
         | import ${classOf[ByteString].getName};
         | public class ${generatedClassName}{
         |    public static RowData ${PbConstant.GENERATED_DECODE_METHOD}(
         |      ${fullMessageClassName} message){
         |        RowData rowData=null;
         |        ${codegenDes.codegen("rowData", "message")}
         |        return rowData;
         |   }
         |}
         |""".stripMargin

    ProtoToRowConverter.LOG.debug("Protobuf decode codegen: \n" + code)
    val generatedClass = PbCodegenUtils.compileClass(
      this.getClass.getClassLoader,
      generatedPackageName + "." + generatedClassName,
      code)
    decodeMethod = generatedClass.getMethod(PbConstant.GENERATED_DECODE_METHOD, messageClass)
    parseFromMethod = messageClass.getMethod(PbConstant.PB_METHOD_PARSE_FROM, classOf[Array[Byte]])
  } catch {
    case ex: Exception =>
      throw new PbCodegenException(ex)
  }

  @throws[Exception]
  def convertProtoBinaryToRow(data: Array[Byte]) = {
    val messageObj = parseFromMethod.invoke(null, data)
    decodeMethod.invoke(null, messageObj).asInstanceOf[RowData]
  }
}
