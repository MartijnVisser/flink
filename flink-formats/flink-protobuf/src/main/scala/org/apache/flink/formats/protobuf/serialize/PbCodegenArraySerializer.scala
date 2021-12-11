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
import org.apache.flink.table.types.logical.LogicalType

/** Serializer to convert flink array type data to proto array type object. */
class PbCodegenArraySerializer(val fd: Descriptors.FieldDescriptor, val elementType: LogicalType,
                               val formatContext: PbFormatContext) extends PbCodegenSerializer {
  @throws[PbCodegenException]
  override def codegen(returnPbVarName: String, internalDataGetStr: String): String = {
    val varUid = PbCodegenVarId.getInstance
    val uid = varUid.getAndIncrement
    val protoTypeStr = PbCodegenUtils.getTypeStrFromProto(fd, false, formatContext.getOuterPrefix)
    val pbListVar = "pbList" + uid
    val arrayDataVar = "arrData" + uid
    val elementDataVar = "eleData" + uid
    val elementPbVar = "elementPbVar" + uid
    val iVar = "i" + uid
    s"""
       |ArrayData ${arrayDataVar} = ${internalDataGetStr};
       |List<${protoTypeStr}> ${pbListVar} = new ArrayList();
       |for(int ${iVar} =0; ${iVar} < ${arrayDataVar}.size(); ${iVar}++){
         |${PbCodegenUtils.generateArrElementCodeWithDefaultValue(arrayDataVar, iVar,
            elementPbVar, elementDataVar, fd, elementType, formatContext)}
         |${pbListVar}.add(${elementPbVar});
       |}
       |${returnPbVarName} = ${pbListVar};
       |""".stripMargin
  }
}
