// Generated from /Users/namenu/Development/lacinia/resources/com/walmartlabs/lacinia/GraphqlSchema.g4 by ANTLR 4.13.1
package com.walmartlabs.lacinia;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link GraphqlSchemaParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface GraphqlSchemaVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#graphqlSchema}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitGraphqlSchema(GraphqlSchemaParser.GraphqlSchemaContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#description}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDescription(GraphqlSchemaParser.DescriptionContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#schemaDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSchemaDef(GraphqlSchemaParser.SchemaDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#operationTypeDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitOperationTypeDef(GraphqlSchemaParser.OperationTypeDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#queryOperationDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitQueryOperationDef(GraphqlSchemaParser.QueryOperationDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#mutationOperationDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMutationOperationDef(GraphqlSchemaParser.MutationOperationDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#subscriptionOperationDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSubscriptionOperationDef(GraphqlSchemaParser.SubscriptionOperationDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#directiveLocationList}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDirectiveLocationList(GraphqlSchemaParser.DirectiveLocationListContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#directiveLocation}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDirectiveLocation(GraphqlSchemaParser.DirectiveLocationContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#executableDirectiveLocation}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExecutableDirectiveLocation(GraphqlSchemaParser.ExecutableDirectiveLocationContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#typeSystemDirectiveLocation}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTypeSystemDirectiveLocation(GraphqlSchemaParser.TypeSystemDirectiveLocationContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#directiveDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDirectiveDef(GraphqlSchemaParser.DirectiveDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#directiveList}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDirectiveList(GraphqlSchemaParser.DirectiveListContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#directive}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDirective(GraphqlSchemaParser.DirectiveContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#directiveArgList}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDirectiveArgList(GraphqlSchemaParser.DirectiveArgListContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#directiveArg}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDirectiveArg(GraphqlSchemaParser.DirectiveArgContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#typeDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTypeDef(GraphqlSchemaParser.TypeDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#typeExtDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTypeExtDef(GraphqlSchemaParser.TypeExtDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#fieldDefs}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFieldDefs(GraphqlSchemaParser.FieldDefsContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#implementationDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitImplementationDef(GraphqlSchemaParser.ImplementationDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#inputTypeDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInputTypeDef(GraphqlSchemaParser.InputTypeDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#inputTypeExtDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInputTypeExtDef(GraphqlSchemaParser.InputTypeExtDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#inputValueDefs}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInputValueDefs(GraphqlSchemaParser.InputValueDefsContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#inputValueDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInputValueDef(GraphqlSchemaParser.InputValueDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#interfaceDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInterfaceDef(GraphqlSchemaParser.InterfaceDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#scalarDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitScalarDef(GraphqlSchemaParser.ScalarDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#unionDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUnionDef(GraphqlSchemaParser.UnionDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#unionExtDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUnionExtDef(GraphqlSchemaParser.UnionExtDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#unionTypes}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUnionTypes(GraphqlSchemaParser.UnionTypesContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#enumDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitEnumDef(GraphqlSchemaParser.EnumDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#enumValueDefs}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitEnumValueDefs(GraphqlSchemaParser.EnumValueDefsContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#enumValueDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitEnumValueDef(GraphqlSchemaParser.EnumValueDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#fieldDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFieldDef(GraphqlSchemaParser.FieldDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#argList}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArgList(GraphqlSchemaParser.ArgListContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#argument}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArgument(GraphqlSchemaParser.ArgumentContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#typeSpec}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTypeSpec(GraphqlSchemaParser.TypeSpecContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#typeName}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTypeName(GraphqlSchemaParser.TypeNameContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#listType}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitListType(GraphqlSchemaParser.ListTypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#required}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRequired(GraphqlSchemaParser.RequiredContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#defaultValue}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDefaultValue(GraphqlSchemaParser.DefaultValueContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#anyName}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAnyName(GraphqlSchemaParser.AnyNameContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#nameTokens}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNameTokens(GraphqlSchemaParser.NameTokensContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#booleanValue}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBooleanValue(GraphqlSchemaParser.BooleanValueContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#value}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitValue(GraphqlSchemaParser.ValueContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#enumValue}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitEnumValue(GraphqlSchemaParser.EnumValueContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#arrayValue}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArrayValue(GraphqlSchemaParser.ArrayValueContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#objectValue}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitObjectValue(GraphqlSchemaParser.ObjectValueContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#objectField}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitObjectField(GraphqlSchemaParser.ObjectFieldContext ctx);
	/**
	 * Visit a parse tree produced by {@link GraphqlSchemaParser#nullValue}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNullValue(GraphqlSchemaParser.NullValueContext ctx);
}