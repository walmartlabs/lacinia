// Generated from java/com/walmartlabs/lacinia/GraphqlSchema.g4 by ANTLR 4.13.1
package com.walmartlabs.lacinia;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link GraphqlSchemaParser}.
 */
public interface GraphqlSchemaListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#graphqlSchema}.
	 * @param ctx the parse tree
	 */
	void enterGraphqlSchema(GraphqlSchemaParser.GraphqlSchemaContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#graphqlSchema}.
	 * @param ctx the parse tree
	 */
	void exitGraphqlSchema(GraphqlSchemaParser.GraphqlSchemaContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#description}.
	 * @param ctx the parse tree
	 */
	void enterDescription(GraphqlSchemaParser.DescriptionContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#description}.
	 * @param ctx the parse tree
	 */
	void exitDescription(GraphqlSchemaParser.DescriptionContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#schemaDef}.
	 * @param ctx the parse tree
	 */
	void enterSchemaDef(GraphqlSchemaParser.SchemaDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#schemaDef}.
	 * @param ctx the parse tree
	 */
	void exitSchemaDef(GraphqlSchemaParser.SchemaDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#operationTypeDef}.
	 * @param ctx the parse tree
	 */
	void enterOperationTypeDef(GraphqlSchemaParser.OperationTypeDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#operationTypeDef}.
	 * @param ctx the parse tree
	 */
	void exitOperationTypeDef(GraphqlSchemaParser.OperationTypeDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#queryOperationDef}.
	 * @param ctx the parse tree
	 */
	void enterQueryOperationDef(GraphqlSchemaParser.QueryOperationDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#queryOperationDef}.
	 * @param ctx the parse tree
	 */
	void exitQueryOperationDef(GraphqlSchemaParser.QueryOperationDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#mutationOperationDef}.
	 * @param ctx the parse tree
	 */
	void enterMutationOperationDef(GraphqlSchemaParser.MutationOperationDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#mutationOperationDef}.
	 * @param ctx the parse tree
	 */
	void exitMutationOperationDef(GraphqlSchemaParser.MutationOperationDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#subscriptionOperationDef}.
	 * @param ctx the parse tree
	 */
	void enterSubscriptionOperationDef(GraphqlSchemaParser.SubscriptionOperationDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#subscriptionOperationDef}.
	 * @param ctx the parse tree
	 */
	void exitSubscriptionOperationDef(GraphqlSchemaParser.SubscriptionOperationDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#directiveLocationList}.
	 * @param ctx the parse tree
	 */
	void enterDirectiveLocationList(GraphqlSchemaParser.DirectiveLocationListContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#directiveLocationList}.
	 * @param ctx the parse tree
	 */
	void exitDirectiveLocationList(GraphqlSchemaParser.DirectiveLocationListContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#directiveLocation}.
	 * @param ctx the parse tree
	 */
	void enterDirectiveLocation(GraphqlSchemaParser.DirectiveLocationContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#directiveLocation}.
	 * @param ctx the parse tree
	 */
	void exitDirectiveLocation(GraphqlSchemaParser.DirectiveLocationContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#executableDirectiveLocation}.
	 * @param ctx the parse tree
	 */
	void enterExecutableDirectiveLocation(GraphqlSchemaParser.ExecutableDirectiveLocationContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#executableDirectiveLocation}.
	 * @param ctx the parse tree
	 */
	void exitExecutableDirectiveLocation(GraphqlSchemaParser.ExecutableDirectiveLocationContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#typeSystemDirectiveLocation}.
	 * @param ctx the parse tree
	 */
	void enterTypeSystemDirectiveLocation(GraphqlSchemaParser.TypeSystemDirectiveLocationContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#typeSystemDirectiveLocation}.
	 * @param ctx the parse tree
	 */
	void exitTypeSystemDirectiveLocation(GraphqlSchemaParser.TypeSystemDirectiveLocationContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#directiveDef}.
	 * @param ctx the parse tree
	 */
	void enterDirectiveDef(GraphqlSchemaParser.DirectiveDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#directiveDef}.
	 * @param ctx the parse tree
	 */
	void exitDirectiveDef(GraphqlSchemaParser.DirectiveDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#directiveList}.
	 * @param ctx the parse tree
	 */
	void enterDirectiveList(GraphqlSchemaParser.DirectiveListContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#directiveList}.
	 * @param ctx the parse tree
	 */
	void exitDirectiveList(GraphqlSchemaParser.DirectiveListContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#directive}.
	 * @param ctx the parse tree
	 */
	void enterDirective(GraphqlSchemaParser.DirectiveContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#directive}.
	 * @param ctx the parse tree
	 */
	void exitDirective(GraphqlSchemaParser.DirectiveContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#directiveArgList}.
	 * @param ctx the parse tree
	 */
	void enterDirectiveArgList(GraphqlSchemaParser.DirectiveArgListContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#directiveArgList}.
	 * @param ctx the parse tree
	 */
	void exitDirectiveArgList(GraphqlSchemaParser.DirectiveArgListContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#directiveArg}.
	 * @param ctx the parse tree
	 */
	void enterDirectiveArg(GraphqlSchemaParser.DirectiveArgContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#directiveArg}.
	 * @param ctx the parse tree
	 */
	void exitDirectiveArg(GraphqlSchemaParser.DirectiveArgContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#typeDef}.
	 * @param ctx the parse tree
	 */
	void enterTypeDef(GraphqlSchemaParser.TypeDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#typeDef}.
	 * @param ctx the parse tree
	 */
	void exitTypeDef(GraphqlSchemaParser.TypeDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#typeExtDef}.
	 * @param ctx the parse tree
	 */
	void enterTypeExtDef(GraphqlSchemaParser.TypeExtDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#typeExtDef}.
	 * @param ctx the parse tree
	 */
	void exitTypeExtDef(GraphqlSchemaParser.TypeExtDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#fieldDefs}.
	 * @param ctx the parse tree
	 */
	void enterFieldDefs(GraphqlSchemaParser.FieldDefsContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#fieldDefs}.
	 * @param ctx the parse tree
	 */
	void exitFieldDefs(GraphqlSchemaParser.FieldDefsContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#implementationDef}.
	 * @param ctx the parse tree
	 */
	void enterImplementationDef(GraphqlSchemaParser.ImplementationDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#implementationDef}.
	 * @param ctx the parse tree
	 */
	void exitImplementationDef(GraphqlSchemaParser.ImplementationDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#inputTypeDef}.
	 * @param ctx the parse tree
	 */
	void enterInputTypeDef(GraphqlSchemaParser.InputTypeDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#inputTypeDef}.
	 * @param ctx the parse tree
	 */
	void exitInputTypeDef(GraphqlSchemaParser.InputTypeDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#inputTypeExtDef}.
	 * @param ctx the parse tree
	 */
	void enterInputTypeExtDef(GraphqlSchemaParser.InputTypeExtDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#inputTypeExtDef}.
	 * @param ctx the parse tree
	 */
	void exitInputTypeExtDef(GraphqlSchemaParser.InputTypeExtDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#inputValueDefs}.
	 * @param ctx the parse tree
	 */
	void enterInputValueDefs(GraphqlSchemaParser.InputValueDefsContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#inputValueDefs}.
	 * @param ctx the parse tree
	 */
	void exitInputValueDefs(GraphqlSchemaParser.InputValueDefsContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#inputValueDef}.
	 * @param ctx the parse tree
	 */
	void enterInputValueDef(GraphqlSchemaParser.InputValueDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#inputValueDef}.
	 * @param ctx the parse tree
	 */
	void exitInputValueDef(GraphqlSchemaParser.InputValueDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#interfaceDef}.
	 * @param ctx the parse tree
	 */
	void enterInterfaceDef(GraphqlSchemaParser.InterfaceDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#interfaceDef}.
	 * @param ctx the parse tree
	 */
	void exitInterfaceDef(GraphqlSchemaParser.InterfaceDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#scalarDef}.
	 * @param ctx the parse tree
	 */
	void enterScalarDef(GraphqlSchemaParser.ScalarDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#scalarDef}.
	 * @param ctx the parse tree
	 */
	void exitScalarDef(GraphqlSchemaParser.ScalarDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#unionDef}.
	 * @param ctx the parse tree
	 */
	void enterUnionDef(GraphqlSchemaParser.UnionDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#unionDef}.
	 * @param ctx the parse tree
	 */
	void exitUnionDef(GraphqlSchemaParser.UnionDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#unionExtDef}.
	 * @param ctx the parse tree
	 */
	void enterUnionExtDef(GraphqlSchemaParser.UnionExtDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#unionExtDef}.
	 * @param ctx the parse tree
	 */
	void exitUnionExtDef(GraphqlSchemaParser.UnionExtDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#unionTypes}.
	 * @param ctx the parse tree
	 */
	void enterUnionTypes(GraphqlSchemaParser.UnionTypesContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#unionTypes}.
	 * @param ctx the parse tree
	 */
	void exitUnionTypes(GraphqlSchemaParser.UnionTypesContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#enumDef}.
	 * @param ctx the parse tree
	 */
	void enterEnumDef(GraphqlSchemaParser.EnumDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#enumDef}.
	 * @param ctx the parse tree
	 */
	void exitEnumDef(GraphqlSchemaParser.EnumDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#enumValueDefs}.
	 * @param ctx the parse tree
	 */
	void enterEnumValueDefs(GraphqlSchemaParser.EnumValueDefsContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#enumValueDefs}.
	 * @param ctx the parse tree
	 */
	void exitEnumValueDefs(GraphqlSchemaParser.EnumValueDefsContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#enumValueDef}.
	 * @param ctx the parse tree
	 */
	void enterEnumValueDef(GraphqlSchemaParser.EnumValueDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#enumValueDef}.
	 * @param ctx the parse tree
	 */
	void exitEnumValueDef(GraphqlSchemaParser.EnumValueDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#fieldDef}.
	 * @param ctx the parse tree
	 */
	void enterFieldDef(GraphqlSchemaParser.FieldDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#fieldDef}.
	 * @param ctx the parse tree
	 */
	void exitFieldDef(GraphqlSchemaParser.FieldDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#argList}.
	 * @param ctx the parse tree
	 */
	void enterArgList(GraphqlSchemaParser.ArgListContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#argList}.
	 * @param ctx the parse tree
	 */
	void exitArgList(GraphqlSchemaParser.ArgListContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#argument}.
	 * @param ctx the parse tree
	 */
	void enterArgument(GraphqlSchemaParser.ArgumentContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#argument}.
	 * @param ctx the parse tree
	 */
	void exitArgument(GraphqlSchemaParser.ArgumentContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#typeSpec}.
	 * @param ctx the parse tree
	 */
	void enterTypeSpec(GraphqlSchemaParser.TypeSpecContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#typeSpec}.
	 * @param ctx the parse tree
	 */
	void exitTypeSpec(GraphqlSchemaParser.TypeSpecContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#typeName}.
	 * @param ctx the parse tree
	 */
	void enterTypeName(GraphqlSchemaParser.TypeNameContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#typeName}.
	 * @param ctx the parse tree
	 */
	void exitTypeName(GraphqlSchemaParser.TypeNameContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#listType}.
	 * @param ctx the parse tree
	 */
	void enterListType(GraphqlSchemaParser.ListTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#listType}.
	 * @param ctx the parse tree
	 */
	void exitListType(GraphqlSchemaParser.ListTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#required}.
	 * @param ctx the parse tree
	 */
	void enterRequired(GraphqlSchemaParser.RequiredContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#required}.
	 * @param ctx the parse tree
	 */
	void exitRequired(GraphqlSchemaParser.RequiredContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#defaultValue}.
	 * @param ctx the parse tree
	 */
	void enterDefaultValue(GraphqlSchemaParser.DefaultValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#defaultValue}.
	 * @param ctx the parse tree
	 */
	void exitDefaultValue(GraphqlSchemaParser.DefaultValueContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#anyName}.
	 * @param ctx the parse tree
	 */
	void enterAnyName(GraphqlSchemaParser.AnyNameContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#anyName}.
	 * @param ctx the parse tree
	 */
	void exitAnyName(GraphqlSchemaParser.AnyNameContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#nameTokens}.
	 * @param ctx the parse tree
	 */
	void enterNameTokens(GraphqlSchemaParser.NameTokensContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#nameTokens}.
	 * @param ctx the parse tree
	 */
	void exitNameTokens(GraphqlSchemaParser.NameTokensContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#booleanValue}.
	 * @param ctx the parse tree
	 */
	void enterBooleanValue(GraphqlSchemaParser.BooleanValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#booleanValue}.
	 * @param ctx the parse tree
	 */
	void exitBooleanValue(GraphqlSchemaParser.BooleanValueContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#value}.
	 * @param ctx the parse tree
	 */
	void enterValue(GraphqlSchemaParser.ValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#value}.
	 * @param ctx the parse tree
	 */
	void exitValue(GraphqlSchemaParser.ValueContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#enumValue}.
	 * @param ctx the parse tree
	 */
	void enterEnumValue(GraphqlSchemaParser.EnumValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#enumValue}.
	 * @param ctx the parse tree
	 */
	void exitEnumValue(GraphqlSchemaParser.EnumValueContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#arrayValue}.
	 * @param ctx the parse tree
	 */
	void enterArrayValue(GraphqlSchemaParser.ArrayValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#arrayValue}.
	 * @param ctx the parse tree
	 */
	void exitArrayValue(GraphqlSchemaParser.ArrayValueContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#objectValue}.
	 * @param ctx the parse tree
	 */
	void enterObjectValue(GraphqlSchemaParser.ObjectValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#objectValue}.
	 * @param ctx the parse tree
	 */
	void exitObjectValue(GraphqlSchemaParser.ObjectValueContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#objectField}.
	 * @param ctx the parse tree
	 */
	void enterObjectField(GraphqlSchemaParser.ObjectFieldContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#objectField}.
	 * @param ctx the parse tree
	 */
	void exitObjectField(GraphqlSchemaParser.ObjectFieldContext ctx);
	/**
	 * Enter a parse tree produced by {@link GraphqlSchemaParser#nullValue}.
	 * @param ctx the parse tree
	 */
	void enterNullValue(GraphqlSchemaParser.NullValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link GraphqlSchemaParser#nullValue}.
	 * @param ctx the parse tree
	 */
	void exitNullValue(GraphqlSchemaParser.NullValueContext ctx);
}