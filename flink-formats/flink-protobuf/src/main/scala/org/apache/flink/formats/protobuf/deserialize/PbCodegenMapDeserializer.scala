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
import org.apache.flink.table.types.logical.MapType

/** Deserializer to convert proto map type object to flink map type data. */
class PbCodegenMapDeserializer(val fd: Descriptors.FieldDescriptor,
                               val mapType: MapType,
                               val formatConfig: PbFormatConfig) extends PbCodegenDeserializer {
  @throws[PbCodegenException]
  override def codegen(returnInternalDataVarName: String, pbGetStr: String): String = {
    // The type of pbGetStr is a native Map object,
    // it should be converted to MapData of flink internal type
    val varUid = PbCodegenVarId.getInstance
    val uid = varUid.getAndIncrement
    val keyType = mapType.getKeyType
    val valueType = mapType.getValueType
    val keyFd = fd.getMessageType.findFieldByName(PbConstant.PB_MAP_KEY_NAME)
    val valueFd = fd.getMessageType.findFieldByName(PbConstant.PB_MAP_VALUE_NAME)
    val pbKeyTypeStr = PbCodegenUtils.getTypeStrFromProto(keyFd, false)
    val pbValueTypeStr = PbCodegenUtils.getTypeStrFromProto(valueFd, false)
    val pbMapVar = "pbMap" + uid
    val pbMapEntryVar = "pbEntry" + uid
    val resultDataMapVar = "resultDataMap" + uid
    val keyDataVar = "keyDataVar" + uid
    val valueDataVar = "valueDataVar" + uid
    val keyDes = PbCodegenDeserializeFactory.getPbCodegenDes(keyFd, keyType, formatConfig)
    val valueDes = PbCodegenDeserializeFactory.getPbCodegenDes(valueFd, valueType, formatConfig)
    s"""
       | Map<${pbKeyTypeStr}, ${pbValueTypeStr}> ${pbMapVar} = ${pbGetStr};
       | Map ${resultDataMapVar} = new HashMap();
       | for(Map.Entry<${pbKeyTypeStr}, ${pbValueTypeStr}> ${pbMapEntryVar}:${pbMapVar}.entrySet()){
       |   Object ${keyDataVar} = null;
       |   Object ${valueDataVar} = null;
       |   ${keyDes.codegen(
                keyDataVar, "((" + pbKeyTypeStr + ")" + pbMapEntryVar + ".getKey())")}
       |   ${valueDes.codegen(valueDataVar,
      "((" + pbValueTypeStr + ")" + pbMapEntryVar + ".getValue())")}
       |   ${resultDataMapVar}.put(${keyDataVar}, ${valueDataVar});
       | }
       | ${returnInternalDataVarName} = new GenericMapData(${resultDataMapVar});
       |""".stripMargin
  }
}
