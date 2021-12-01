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
package org.apache.flink.formats.protobuf.serialize

import com.google.protobuf.{AbstractMessage, ByteString, Descriptors}
import org.apache.flink.formats.protobuf._
import org.apache.flink.formats.protobuf.deserialize.ProtoToRowConverter
import org.apache.flink.table.data.{ArrayData, RowData, StringData}
import org.apache.flink.table.types.logical.RowType
import org.slf4j.LoggerFactory

import java.lang.reflect.Method
import java.util.UUID

/**
 * {@link RowToProtoConverter} can convert flink row data to binary protobuf message data by codegen
 * process.
 */
object RowToProtoConverter {
  private val LOG = LoggerFactory.getLogger(classOf[RowToProtoConverter])
}

class RowToProtoConverter @throws[PbCodegenException]
(val rowType: RowType, val formatConfig: PbFormatConfig) {
  final private var encodeMethod: Method = null

  try {
    val descriptor = PbFormatUtils.getDescriptor(formatConfig.getMessageClassName)
    val uuid = UUID.randomUUID.toString.replaceAll("\\-", "")
    val generatedClassName = "GeneratedRowToProto_" + uuid
    val generatedPackageName = classOf[RowToProtoConverter].getPackage.getName
    val codegenSer = PbCodegenSerializeFactory.getPbCodegenTopRowSer(
      descriptor, rowType, formatConfig)
    val code =
      s"""
         |package ${generatedPackageName};
         |import ${classOf[AbstractMessage].getName};
         |import ${classOf[Descriptors].getName};
         |import ${classOf[RowData].getName};
         |import ${classOf[ArrayData].getName};
         |import ${classOf[StringData].getName};
         |import ${classOf[ByteString].getName};
         |import ${classOf[java.util.List[_]].getName};
         |import ${classOf[java.util.ArrayList[_]].getName};
         |import ${classOf[java.util.Map[_, _]].getName};
         |import ${classOf[java.util.HashMap[_, _]].getName};
         |public class ${generatedClassName}{
         |   public static AbstractMessage ${PbConstant.GENERATED_ENCODE_METHOD}(RowData rowData){
         |     AbstractMessage message = null;
         |     ${codegenSer.codegen("message", "rowData")}
         |     return message;
         |   }
         |}
         |""".stripMargin
    RowToProtoConverter.LOG.debug("Protobuf encode codegen: \n" + code)
    val generatedClass = PbCodegenUtils.compileClass(this.getClass.getClassLoader,
      generatedPackageName + "." + generatedClassName, code)
    encodeMethod = generatedClass.getMethod(PbConstant.GENERATED_ENCODE_METHOD, classOf[RowData])
  } catch {
    case ex: Exception =>
      throw new PbCodegenException(ex)
  }

  @throws[Exception]
  def convertRowToProtoBinary(rowData: RowData) = {
    val message = encodeMethod.invoke(null, rowData).asInstanceOf[AbstractMessage]
    message.toByteArray
  }
}
