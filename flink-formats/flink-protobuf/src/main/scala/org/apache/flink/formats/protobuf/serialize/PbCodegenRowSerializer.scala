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

import com.google.protobuf.Descriptors
import org.apache.flink.formats.protobuf._
import org.apache.flink.table.types.logical.{LogicalTypeRoot, RowType}

import scala.collection.JavaConverters._

/** Serializer to convert flink row type data to proto row type object. */
class PbCodegenRowSerializer(val descriptor: Descriptors.Descriptor, val rowType: RowType,
                             val formatConfig: PbFormatConfig) extends PbCodegenSerializer {
  @throws[PbCodegenException]
  override def codegen(returnPbVarName: String, internalDataGetStr: String) = {
    val varUid = PbCodegenVarId.getInstance
    val uid = varUid.getAndIncrement
    val sb = new StringBuilder
    val rowDataVar = "rowData" + uid
    val pbMessageTypeStr = PbFormatUtils.getFullJavaName(descriptor)
    val messageBuilderVar = "messageBuilder" + uid
    sb.append(
      s"""
         |RowData ${rowDataVar} = ${internalDataGetStr};
         |${pbMessageTypeStr}.Builder ${messageBuilderVar} = ${pbMessageTypeStr}.newBuilder();
         |""".stripMargin)
    var index = 0
    for (fieldName <- rowType.getFieldNames.asScala) {
      val elementFd = descriptor.findFieldByName(fieldName)
      val subType = rowType.getTypeAt(rowType.getFieldIndex(fieldName))
      val subUid = varUid.getAndIncrement
      val elementPbVar = "elementPbVar" + subUid
      val elementPbTypeStr = if (elementFd.isMapField) {
        PbCodegenUtils.getTypeStrFromProto(elementFd, false)
      }
      else {
        PbCodegenUtils.getTypeStrFromProto(elementFd, PbFormatUtils.isArrayType(subType))
      }
      val strongCamelFieldName = PbFormatUtils.getStrongCamelCaseJsonName(fieldName)

      val subRowGetCode = PbCodegenUtils.getContainerDataFieldGetterCodePhrase(
        rowDataVar, index + "", subType)
      val codegen = PbCodegenSerializeFactory.getPbCodegenSer(elementFd, subType, formatConfig)
      // Only set non-null element of flink row to proto object. The real value in proto
      // result depends on protobuf implementation.
      sb.append(
        s"""
           |if(!${rowDataVar}.isNullAt(${index})){
           |  ${elementPbTypeStr} ${elementPbVar};
           |  ${codegen.codegen(elementPbVar, subRowGetCode)}
           |  ${
                  if (subType.getTypeRoot == LogicalTypeRoot.ARRAY) {
                    s"${messageBuilderVar}.addAll${strongCamelFieldName}(${elementPbVar});"
                  }
                  else if (subType.getTypeRoot == LogicalTypeRoot.MAP) {
                    s"${messageBuilderVar}.putAll${strongCamelFieldName}(${elementPbVar});"
                  } else {
                    s"${messageBuilderVar}.set${strongCamelFieldName}(${elementPbVar});"
                  }
               }
           |}
           |""".stripMargin
      )
      index += 1
    }
    sb.append(s"${returnPbVarName} = ${messageBuilderVar}.build();")
    sb.toString()
  }
}
