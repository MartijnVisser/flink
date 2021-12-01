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
package org.apache.flink.formats.protobuf

import com.google.protobuf.Descriptors
import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType
import org.apache.flink.api.common.InvalidProgramException
import org.apache.flink.formats.protobuf.serialize.PbCodegenSerializeFactory
import org.apache.flink.table.types.logical.{LogicalType, LogicalTypeRoot}
import org.codehaus.janino.SimpleCompiler
import org.slf4j.LoggerFactory

/** Codegen utils only used in protobuf format. */
object PbCodegenUtils {
  private val LOG = LoggerFactory.getLogger(classOf[PbCodegenUtils])

  /**
   * @param dataGetter code phrase which represent flink container type like row/array in codegen
   *                   sections
   * @param index      the index number in flink container type
   * @param eleType    the element type
   */
  def getContainerDataFieldGetterCodePhrase(dataGetter: String,
                                            index: String,
                                            eleType: LogicalType
                                           ): String = eleType.getTypeRoot match {
    case LogicalTypeRoot.INTEGER =>
      dataGetter + ".getInt(" + index + ")"
    case LogicalTypeRoot.BIGINT =>
      dataGetter + ".getLong(" + index + ")"
    case LogicalTypeRoot.FLOAT =>
      dataGetter + ".getFloat(" + index + ")"
    case LogicalTypeRoot.DOUBLE =>
      dataGetter + ".getDouble(" + index + ")"
    case LogicalTypeRoot.BOOLEAN =>
      dataGetter + ".getBoolean(" + index + ")"
    case LogicalTypeRoot.VARCHAR | LogicalTypeRoot.CHAR =>
      dataGetter + ".getString(" + index + ")"
    case LogicalTypeRoot.VARBINARY | LogicalTypeRoot.BINARY =>
      dataGetter + ".getBinary(" + index + ")"
    case LogicalTypeRoot.ROW =>
      val size = eleType.getChildren.size
      dataGetter + ".getRow(" + index + ", " + size + ")"
    case LogicalTypeRoot.MAP =>
      dataGetter + ".getMap(" + index + ")"
    case LogicalTypeRoot.ARRAY =>
      dataGetter + ".getArray(" + index + ")"
    case _ =>
      throw new IllegalArgumentException("Unsupported data type in schema: " + eleType)
  }

  /**
   * Get java type str from {@link FieldDescriptor} which directly fetched from protobuf object.
   *
   * @return The returned code phrase will be used as java type str in codegen sections.
   * @throws PbCodegenException
   */
  @throws[PbCodegenException]
  def getTypeStrFromProto(fd: Descriptors.FieldDescriptor, isList: Boolean): String = {
    var typeStr: String = null
    fd.getJavaType match {
      case JavaType.MESSAGE =>
        if (fd.isMapField) { // map
          val keyFd = fd.getMessageType.findFieldByName(PbConstant.PB_MAP_KEY_NAME)
          val valueFd = fd.getMessageType.findFieldByName(PbConstant.PB_MAP_VALUE_NAME)
          // key and value cannot be repeated
          val keyTypeStr = getTypeStrFromProto(keyFd, false)
          val valueTypeStr = getTypeStrFromProto(valueFd, false)
          typeStr = "Map<" + keyTypeStr + "," + valueTypeStr + ">"
        }
        else { // simple message
          typeStr = PbFormatUtils.getFullJavaName(fd.getMessageType)
        }
      case JavaType.INT =>
        typeStr = "Integer"
      case JavaType.LONG =>
        typeStr = "Long"
      case JavaType.STRING =>
        typeStr = "String"
      case JavaType.ENUM =>
        typeStr = PbFormatUtils.getFullJavaName(fd.getEnumType)
      case JavaType.FLOAT =>
        typeStr = "Float"
      case JavaType.DOUBLE =>
        typeStr = "Double"
      case JavaType.BYTE_STRING =>
        typeStr = "ByteString"
      case JavaType.BOOLEAN =>
        typeStr = "Boolean"
      case _ =>
        throw new PbCodegenException("do not support field type: " + fd.getJavaType)
    }
    if (isList) {
      "List<" + typeStr + ">"
    }
    else {
      typeStr
    }
  }

  /**
   * Get java type str from {@link LogicalType} which directly fetched from flink type.
   *
   * @return The returned code phrase will be used as java type str in codegen sections.
   */
  def getTypeStrFromLogicType(`type`: LogicalType) = `type`.getTypeRoot match {
    case LogicalTypeRoot.INTEGER =>
      "int"
    case LogicalTypeRoot.BIGINT =>
      "long"
    case LogicalTypeRoot.FLOAT =>
      "float"
    case LogicalTypeRoot.DOUBLE =>
      "double"
    case LogicalTypeRoot.BOOLEAN =>
      "boolean"
    case LogicalTypeRoot.VARCHAR | LogicalTypeRoot.CHAR =>
      "StringData"
    case LogicalTypeRoot.VARBINARY | LogicalTypeRoot.BINARY =>
      "byte[]"
    case LogicalTypeRoot.ROW =>
      "RowData"
    case LogicalTypeRoot.MAP =>
      "MapData"
    case LogicalTypeRoot.ARRAY =>
      "ArrayData"
    case _ =>
      throw new IllegalArgumentException("Unsupported data type in schema: " + `type`)
  }

  /**
   * Get protobuf default value from {@link FieldDescriptor}.
   *
   * @return The java code phrase which represents default value calculation.
   */
  @throws[PbCodegenException]
  def getDefaultPbValue(fieldDescriptor: Descriptors.FieldDescriptor,
                        nullLiteral: String) = fieldDescriptor.getJavaType match {
    case JavaType.MESSAGE =>
      PbFormatUtils.getFullJavaName(fieldDescriptor.getMessageType) + ".getDefaultInstance()"
    case JavaType.INT =>
      "0"
    case JavaType.LONG =>
      "0L"
    case JavaType.STRING =>
      "\"" + nullLiteral + "\""
    case JavaType.ENUM =>
      PbFormatUtils.getFullJavaName(fieldDescriptor.getEnumType) + ".values()[0]"
    case JavaType.FLOAT =>
      "0.0f"
    case JavaType.DOUBLE =>
      "0.0d"
    case JavaType.BYTE_STRING =>
      "ByteString.EMPTY"
    case JavaType.BOOLEAN =>
      "false"
    case _ =>
      throw new PbCodegenException("do not support field type: " + fieldDescriptor.getJavaType)
  }

  /**
   * This method will be called from row serializer of array/map type because flink contains both
   * array/map type in array format. Map/Arr cannot contain null value in proto object so we must
   * do conversion in case of null values in map/arr type.
   *
   * @param arrDataVar      code phrase represent arrayData of arr type or keyData/valueData in map
   *                        type.
   * @param iVar            the index in arrDataVar
   * @param pbVar           the returned pb variable name in codegen.
   * @param dataVar         the input variable from flink row
   * @param elementPbFd     {@link FieldDescriptor} of element type in proto object
   * @param elementDataType {@link LogicalType} of element type in flink object
   * @return The java code segment which represents field value retrieval.
   */
  @throws[PbCodegenException]
  def generateArrElementCodeWithDefaultValue(arrDataVar: String, iVar: String,
                                             pbVar: String,
                                             dataVar: String,
                                             elementPbFd: Descriptors.FieldDescriptor,
                                             elementDataType: LogicalType,
                                             pbFormatConfig: PbFormatConfig) = {
    val protoTypeStr = PbCodegenUtils.getTypeStrFromProto(elementPbFd, false)
    val dataTypeStr = PbCodegenUtils.getTypeStrFromLogicType(elementDataType)
    val codegenSer = PbCodegenSerializeFactory.getPbCodegenSer(
      elementPbFd, elementDataType, pbFormatConfig)
    s"""
       |${protoTypeStr} ${pbVar};
       |if(${arrDataVar}.isNullAt(${iVar})){
       |   ${pbVar} = ${
      PbCodegenUtils.getDefaultPbValue(elementPbFd,
        pbFormatConfig.getWriteNullStringLiterals)
    };
       |}else{
       |   ${dataTypeStr} ${dataVar};
       |   ${dataVar} = ${
      PbCodegenUtils.getContainerDataFieldGetterCodePhrase(
        arrDataVar, iVar, elementDataType)
    };
       |   ${codegenSer.codegen(pbVar, dataVar)}
       |}
       |""".stripMargin
  }

  @throws[ClassNotFoundException]
  def compileClass(classloader: ClassLoader, className: String, code: String) = {
    val simpleCompiler = new SimpleCompiler
    simpleCompiler.setParentClassLoader(classloader)
    try {
      simpleCompiler.cook(code)
    } catch {
      case t: Throwable =>
        LOG.error("Protobuf codegen compile error: \n" + code)
        throw new InvalidProgramException("Program cannot be compiled. " +
          "This is a bug. Please file an issue.", t)
    }
    simpleCompiler.getClassLoader.loadClass(className)
  }
}

class PbCodegenUtils{}
