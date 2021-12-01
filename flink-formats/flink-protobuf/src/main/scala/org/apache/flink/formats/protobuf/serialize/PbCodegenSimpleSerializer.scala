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
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType
import org.apache.flink.formats.protobuf.{PbCodegenVarId, PbFormatConfig, PbFormatUtils}
import org.apache.flink.table.types.logical.{LogicalType, LogicalTypeRoot}

/** Serializer to convert flink simple type data to proto simple type object. */
class PbCodegenSimpleSerializer(val fd: Descriptors.FieldDescriptor, val `type`: LogicalType,
                                val formatConfig: PbFormatConfig) extends PbCodegenSerializer {
  /**
   * @param internalDataGetStr the real value of {@code internalDataGetStr} may be String, int,
   *                           long, double, float, boolean, byte[], enum value
   *                           {@code internalDataGetStr} must not be null.
   */
  override def codegen(returnPbVarName: String, internalDataGetStr: String): String =
    `type`.getTypeRoot match {
      case LogicalTypeRoot.INTEGER | LogicalTypeRoot.BIGINT | LogicalTypeRoot.BIGINT
           | LogicalTypeRoot.FLOAT | LogicalTypeRoot.DOUBLE | LogicalTypeRoot.BOOLEAN =>
        returnPbVarName + " = " + internalDataGetStr + ";"
      case LogicalTypeRoot.VARCHAR | LogicalTypeRoot.CHAR =>
        val sb = new StringBuilder
        val uid = PbCodegenVarId.getInstance.getAndIncrement
        val fromVar = "fromVar" + uid
        sb.append(
          s"""
             |String ${fromVar};
             |${fromVar} = ${internalDataGetStr}.toString();
             |""".stripMargin)
        val assignmentCode: String = if (fd.getJavaType == JavaType.ENUM) {
          val enumValueDescVar = "enumValueDesc" + uid
          val enumTypeStr = PbFormatUtils.getFullJavaName(fd.getEnumType)
          s"""
             |Descriptors.EnumValueDescriptor ${enumValueDescVar} =
             |${enumTypeStr}.getDescriptor().findValueByName(${fromVar});
             |if(null == ${enumValueDescVar}){
             |  // choose the first enum element as default value if such value is invalid enum
             |  ${returnPbVarName} = ${enumTypeStr}.values()[0];
             |}else{
             |  // choose the exact enum value
             |  ${returnPbVarName} = ${enumTypeStr}.valueOf(${enumValueDescVar});
             |}
             |""".stripMargin
        } else {
          s"${returnPbVarName} = ${fromVar};"
        }
        sb.append(assignmentCode)
        sb.toString()
      case LogicalTypeRoot.VARBINARY | LogicalTypeRoot.BINARY =>
        returnPbVarName + " = ByteString.copyFrom(" + internalDataGetStr + ");"
      case _ =>
        throw new IllegalArgumentException("Unsupported data type in schema: " + `type`)
    }
}
