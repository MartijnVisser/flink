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

import com.google.protobuf.Descriptors
import org.apache.flink.formats.protobuf._
import org.apache.flink.table.types.logical.RowType

import scala.collection.JavaConverters._

/** Deserializer to convert proto message type object to flink row type data. */
class PbCodegenRowDeserializer(val descriptor: Descriptors.Descriptor,
                               val rowType: RowType,
                               val formatConfig: PbFormatConfig) extends PbCodegenDeserializer {
  @throws[PbCodegenException]
  override def codegen(returnInternalDataVarName: String, pbGetStr: String): String = {
    // The type of pbGetStr is a native protobuf object,
    // it should be converted to RowData of flink internal type
    val varUid = PbCodegenVarId.getInstance
    val uid = varUid.getAndIncrement
    val pbMessageVar = "message" + uid
    val rowDataVar = "rowData" + uid
    val fieldSize = rowType.getFieldNames.size
    val pbMessageTypeStr = PbFormatUtils.getFullJavaName(descriptor)
    val codeSb = new StringBuilder;
    codeSb.append(
      s"""
         |${pbMessageTypeStr} ${pbMessageVar} = ${pbGetStr};
         |GenericRowData ${rowDataVar} = new GenericRowData(${fieldSize});
         |""".stripMargin)
    var index = 0;
    for (fieldName <- rowType.getFieldNames.asScala) {
      val subUid = varUid.getAndIncrement
      val elementDataVar = "elementDataVar" + subUid
      val subType = rowType.getTypeAt(rowType.getFieldIndex(fieldName))
      val elementFd = descriptor.findFieldByName(fieldName)
      val strongCamelFieldName = PbFormatUtils.getStrongCamelCaseJsonName(fieldName)
      val codegen = PbCodegenDeserializeFactory.getPbCodegenDes(elementFd, subType, formatConfig)
      val elementMessageGetStr = pbMessageElementGetStr(pbMessageVar, strongCamelFieldName,
        elementFd, PbFormatUtils.isArrayType(subType))
      if (!formatConfig.isReadDefaultValues) {
        val isMessageNonEmptyCode = isMessageNonEmptyStr(
          pbMessageVar, strongCamelFieldName, PbFormatUtils.isRepeatedType(subType))
        codeSb.append(
          s"""
             |Object ${elementDataVar} = null;
             |if(${isMessageNonEmptyCode}){
             |  ${codegen.codegen(elementDataVar, elementMessageGetStr)}
             |}
             |${rowDataVar}.setField(${index}, ${elementDataVar});
             |""".stripMargin)
      } else {
        // readDefaultValues must be true in pb3 mode
        codeSb.append(
          s"""
             |Object ${elementDataVar} = null;
             |${codegen.codegen(elementDataVar, elementMessageGetStr)}
             |${rowDataVar}.setField(${index}, ${elementDataVar});
             |""".stripMargin)
      }
      index += 1;
    }
    codeSb.append(s"${returnInternalDataVarName} = ${rowDataVar};")
    codeSb.toString()
  }

  private def pbMessageElementGetStr(message: String,
                                     fieldName: String,
                                     fd: Descriptors.FieldDescriptor,
                                     isList: Boolean) = {
    if (fd.isMapField) { // map
      message + ".get" + fieldName + "Map()"
    } else if (isList) { // list
      message + ".get" + fieldName + "List()"
    } else message + ".get" + fieldName + "()"
  }

  private def isMessageNonEmptyStr(message: String, fieldName: String, isListOrMap: Boolean) = {
    if (isListOrMap) {
      message + ".get" + fieldName + "Count() > 0"
    }
    else { // proto syntax class do not have hasName() interface
      message + ".has" + fieldName + "()"
    }
  }
}
