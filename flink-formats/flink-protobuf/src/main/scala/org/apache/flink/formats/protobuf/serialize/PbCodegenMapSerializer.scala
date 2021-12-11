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
import org.apache.flink.table.types.logical.MapType

/** Serializer to convert flink map type data to proto map type object. */
class PbCodegenMapSerializer(val fd: Descriptors.FieldDescriptor,
                             val mapType: MapType,
                             val formatContext: PbFormatContext) extends PbCodegenSerializer {
  @throws[PbCodegenException]
  override def codegen(returnPbVarName: String, internalDataGetStr: String) = {
    val varUid = PbCodegenVarId.getInstance
    val uid = varUid.getAndIncrement
    val keyType = mapType.getKeyType
    val valueType = mapType.getValueType
    val keyFd = fd.getMessageType.findFieldByName(PbConstant.PB_MAP_KEY_NAME)
    val valueFd = fd.getMessageType.findFieldByName(PbConstant.PB_MAP_VALUE_NAME)
    val keyProtoTypeStr = PbCodegenUtils.getTypeStrFromProto(keyFd, false,
      formatContext.getOuterPrefix)
    val valueProtoTypeStr = PbCodegenUtils.getTypeStrFromProto(valueFd, false,
      formatContext.getOuterPrefix)
    val keyArrDataVar = "keyArrData" + uid
    val valueArrDataVar = "valueArrData" + uid
    val iVar = "i" + uid
    val pbMapVar = "resultPbMap" + uid
    val keyPbVar = "keyPbVar" + uid
    val valuePbVar = "valuePbVar" + uid
    val keyDataVar = "keyDataVar" + uid
    val valueDataVar = "valueDataVar" + uid

    s"""
       |ArrayData ${keyArrDataVar} = ${internalDataGetStr}.keyArray();
       |ArrayData ${valueArrDataVar} = ${internalDataGetStr}.valueArray();
       |Map<${keyProtoTypeStr}, ${valueProtoTypeStr}> ${pbMapVar} = new HashMap();
       |for(int ${iVar} = 0; ${iVar} < ${keyArrDataVar}.size(); ${iVar}++){
       |  // process key
       |  ${PbCodegenUtils.generateArrElementCodeWithDefaultValue(keyArrDataVar, iVar,
            keyPbVar, keyDataVar, keyFd, keyType, formatContext)}
       |  // process value
       |  ${PbCodegenUtils.generateArrElementCodeWithDefaultValue(valueArrDataVar, iVar,
           valuePbVar, valueDataVar, valueFd, valueType, formatContext)}
       |  ${pbMapVar}.put(${keyPbVar},${valuePbVar});
       |}
       |${returnPbVarName} = ${pbMapVar};
       |""".stripMargin
  }
}
