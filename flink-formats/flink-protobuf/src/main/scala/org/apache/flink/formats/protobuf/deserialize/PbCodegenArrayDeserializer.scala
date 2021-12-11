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
import org.apache.flink.table.types.logical.LogicalType

/** Deserializer to convert proto array type object to flink array type data. */
class PbCodegenArrayDeserializer(val fd: Descriptors.FieldDescriptor,
                                 val elementType: LogicalType,
                                 val formatContext: PbFormatContext) extends PbCodegenDeserializer {
  @throws[PbCodegenException]
  override def codegen(returnInternalDataVarName: String, pbGetStr: String):String = {
    // The type of pbGetStr is a native List object,
    // it should be converted to ArrayData of flink internal type.
    val varUid = PbCodegenVarId.getInstance
    val uid = varUid.getAndIncrement
    val protoTypeStr = PbCodegenUtils.getTypeStrFromProto(fd, false, formatContext.getOuterPrefix)
    val listPbVar = "list" + uid
    val newArrDataVar = "newArr" + uid
    val subReturnDataVar = "subReturnVar" + uid
    val iVar = "i" + uid
    val subPbObjVar = "subObj" + uid
    val codegenDes = PbCodegenDeserializeFactory.getPbCodegenDes(fd, elementType, formatContext)
    s"""
      |List<${protoTypeStr}> ${listPbVar} = ${pbGetStr};
      |Object[] ${newArrDataVar} = new Object[${listPbVar}.size()];
      |for(int ${iVar}=0; ${iVar} < ${listPbVar}.size(); ${iVar}++){
      |  Object ${subReturnDataVar} = null;
      |  ${protoTypeStr} ${subPbObjVar} = (${protoTypeStr})${listPbVar}.get(${iVar});
      |  ${codegenDes.codegen(subReturnDataVar, subPbObjVar)}
      |  ${newArrDataVar}[${iVar}] = ${subReturnDataVar};
      |}
      |${returnInternalDataVarName} = new GenericArrayData(${newArrDataVar});
      |""".stripMargin
  }
}
