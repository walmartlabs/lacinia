// Generated from /Users/namenu/Development/lacinia/resources/com/walmartlabs/lacinia/GraphqlSchema.g4 by ANTLR 4.13.1
package com.walmartlabs.lacinia;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue"})
public class GraphqlSchemaParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.13.1", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, T__5=6, T__6=7, T__7=8, T__8=9, 
		T__9=10, T__10=11, T__11=12, EXECUTABLE_DIRECTIVE_LOCATION=13, TYPE_SYSTEM_DIRECTIVE_LOCATION=14, 
		K_TYPE=15, K_IMPLEMENTS=16, K_INTERFACE=17, K_SCHEMA=18, K_ENUM=19, K_UNION=20, 
		K_INPUT=21, K_DIRECTIVE=22, K_EXTEND=23, K_SCALAR=24, K_ON=25, K_FRAGMENT=26, 
		K_QUERY=27, K_MUTATION=28, K_SUBSCRIPTION=29, K_VALUE=30, K_TRUE=31, K_FALSE=32, 
		K_NULL=33, Name=34, IntValue=35, FloatValue=36, Sign=37, IntegerPart=38, 
		NonZeroDigit=39, ExponentPart=40, Digit=41, StringValue=42, BlockStringValue=43, 
		Ignored=44;
	public static final int
		RULE_graphqlSchema = 0, RULE_description = 1, RULE_schemaDef = 2, RULE_operationTypeDef = 3, 
		RULE_queryOperationDef = 4, RULE_mutationOperationDef = 5, RULE_subscriptionOperationDef = 6, 
		RULE_directiveLocationList = 7, RULE_directiveLocation = 8, RULE_executableDirectiveLocation = 9, 
		RULE_typeSystemDirectiveLocation = 10, RULE_directiveDef = 11, RULE_directiveList = 12, 
		RULE_directive = 13, RULE_directiveArgList = 14, RULE_directiveArg = 15, 
		RULE_typeDef = 16, RULE_typeExtDef = 17, RULE_fieldDefs = 18, RULE_implementationDef = 19, 
		RULE_inputTypeDef = 20, RULE_inputTypeExtDef = 21, RULE_inputValueDefs = 22, 
		RULE_inputValueDef = 23, RULE_interfaceDef = 24, RULE_scalarDef = 25, 
		RULE_unionDef = 26, RULE_unionExtDef = 27, RULE_unionTypes = 28, RULE_enumDef = 29, 
		RULE_enumValueDefs = 30, RULE_enumValueDef = 31, RULE_fieldDef = 32, RULE_argList = 33, 
		RULE_argument = 34, RULE_typeSpec = 35, RULE_typeName = 36, RULE_listType = 37, 
		RULE_required = 38, RULE_defaultValue = 39, RULE_anyName = 40, RULE_nameTokens = 41, 
		RULE_booleanValue = 42, RULE_value = 43, RULE_enumValue = 44, RULE_arrayValue = 45, 
		RULE_objectValue = 46, RULE_objectField = 47, RULE_nullValue = 48;
	private static String[] makeRuleNames() {
		return new String[] {
			"graphqlSchema", "description", "schemaDef", "operationTypeDef", "queryOperationDef", 
			"mutationOperationDef", "subscriptionOperationDef", "directiveLocationList", 
			"directiveLocation", "executableDirectiveLocation", "typeSystemDirectiveLocation", 
			"directiveDef", "directiveList", "directive", "directiveArgList", "directiveArg", 
			"typeDef", "typeExtDef", "fieldDefs", "implementationDef", "inputTypeDef", 
			"inputTypeExtDef", "inputValueDefs", "inputValueDef", "interfaceDef", 
			"scalarDef", "unionDef", "unionExtDef", "unionTypes", "enumDef", "enumValueDefs", 
			"enumValueDef", "fieldDef", "argList", "argument", "typeSpec", "typeName", 
			"listType", "required", "defaultValue", "anyName", "nameTokens", "booleanValue", 
			"value", "enumValue", "arrayValue", "objectValue", "objectField", "nullValue"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'{'", "'}'", "':'", "'|'", "'@'", "'('", "')'", "'&'", "'='", 
			"'['", "']'", "'!'", null, null, "'type'", "'implements'", "'interface'", 
			"'schema'", "'enum'", "'union'", "'input'", "'directive'", "'extend'", 
			"'scalar'", "'on'", "'fragment'", "'query'", "'mutation'", "'subscription'", 
			"'value'", "'true'", "'false'", "'null'", null, null, null, "'-'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, "EXECUTABLE_DIRECTIVE_LOCATION", "TYPE_SYSTEM_DIRECTIVE_LOCATION", 
			"K_TYPE", "K_IMPLEMENTS", "K_INTERFACE", "K_SCHEMA", "K_ENUM", "K_UNION", 
			"K_INPUT", "K_DIRECTIVE", "K_EXTEND", "K_SCALAR", "K_ON", "K_FRAGMENT", 
			"K_QUERY", "K_MUTATION", "K_SUBSCRIPTION", "K_VALUE", "K_TRUE", "K_FALSE", 
			"K_NULL", "Name", "IntValue", "FloatValue", "Sign", "IntegerPart", "NonZeroDigit", 
			"ExponentPart", "Digit", "StringValue", "BlockStringValue", "Ignored"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}

	@Override
	public String getGrammarFileName() { return "GraphqlSchema.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public GraphqlSchemaParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class GraphqlSchemaContext extends ParserRuleContext {
		public List<SchemaDefContext> schemaDef() {
			return getRuleContexts(SchemaDefContext.class);
		}
		public SchemaDefContext schemaDef(int i) {
			return getRuleContext(SchemaDefContext.class,i);
		}
		public List<TypeDefContext> typeDef() {
			return getRuleContexts(TypeDefContext.class);
		}
		public TypeDefContext typeDef(int i) {
			return getRuleContext(TypeDefContext.class,i);
		}
		public List<TypeExtDefContext> typeExtDef() {
			return getRuleContexts(TypeExtDefContext.class);
		}
		public TypeExtDefContext typeExtDef(int i) {
			return getRuleContext(TypeExtDefContext.class,i);
		}
		public List<InputTypeDefContext> inputTypeDef() {
			return getRuleContexts(InputTypeDefContext.class);
		}
		public InputTypeDefContext inputTypeDef(int i) {
			return getRuleContext(InputTypeDefContext.class,i);
		}
		public List<InputTypeExtDefContext> inputTypeExtDef() {
			return getRuleContexts(InputTypeExtDefContext.class);
		}
		public InputTypeExtDefContext inputTypeExtDef(int i) {
			return getRuleContext(InputTypeExtDefContext.class,i);
		}
		public List<UnionDefContext> unionDef() {
			return getRuleContexts(UnionDefContext.class);
		}
		public UnionDefContext unionDef(int i) {
			return getRuleContext(UnionDefContext.class,i);
		}
		public List<UnionExtDefContext> unionExtDef() {
			return getRuleContexts(UnionExtDefContext.class);
		}
		public UnionExtDefContext unionExtDef(int i) {
			return getRuleContext(UnionExtDefContext.class,i);
		}
		public List<EnumDefContext> enumDef() {
			return getRuleContexts(EnumDefContext.class);
		}
		public EnumDefContext enumDef(int i) {
			return getRuleContext(EnumDefContext.class,i);
		}
		public List<InterfaceDefContext> interfaceDef() {
			return getRuleContexts(InterfaceDefContext.class);
		}
		public InterfaceDefContext interfaceDef(int i) {
			return getRuleContext(InterfaceDefContext.class,i);
		}
		public List<ScalarDefContext> scalarDef() {
			return getRuleContexts(ScalarDefContext.class);
		}
		public ScalarDefContext scalarDef(int i) {
			return getRuleContext(ScalarDefContext.class,i);
		}
		public List<DirectiveDefContext> directiveDef() {
			return getRuleContexts(DirectiveDefContext.class);
		}
		public DirectiveDefContext directiveDef(int i) {
			return getRuleContext(DirectiveDefContext.class,i);
		}
		public GraphqlSchemaContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_graphqlSchema; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterGraphqlSchema(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitGraphqlSchema(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitGraphqlSchema(this);
			else return visitor.visitChildren(this);
		}
	}

	public final GraphqlSchemaContext graphqlSchema() throws RecognitionException {
		GraphqlSchemaContext _localctx = new GraphqlSchemaContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_graphqlSchema);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(111);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 13194172989440L) != 0)) {
				{
				setState(109);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,0,_ctx) ) {
				case 1:
					{
					setState(98);
					schemaDef();
					}
					break;
				case 2:
					{
					setState(99);
					typeDef();
					}
					break;
				case 3:
					{
					setState(100);
					typeExtDef();
					}
					break;
				case 4:
					{
					setState(101);
					inputTypeDef();
					}
					break;
				case 5:
					{
					setState(102);
					inputTypeExtDef();
					}
					break;
				case 6:
					{
					setState(103);
					unionDef();
					}
					break;
				case 7:
					{
					setState(104);
					unionExtDef();
					}
					break;
				case 8:
					{
					setState(105);
					enumDef();
					}
					break;
				case 9:
					{
					setState(106);
					interfaceDef();
					}
					break;
				case 10:
					{
					setState(107);
					scalarDef();
					}
					break;
				case 11:
					{
					setState(108);
					directiveDef();
					}
					break;
				}
				}
				setState(113);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class DescriptionContext extends ParserRuleContext {
		public TerminalNode StringValue() { return getToken(GraphqlSchemaParser.StringValue, 0); }
		public TerminalNode BlockStringValue() { return getToken(GraphqlSchemaParser.BlockStringValue, 0); }
		public DescriptionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_description; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterDescription(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitDescription(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitDescription(this);
			else return visitor.visitChildren(this);
		}
	}

	public final DescriptionContext description() throws RecognitionException {
		DescriptionContext _localctx = new DescriptionContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_description);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(114);
			_la = _input.LA(1);
			if ( !(_la==StringValue || _la==BlockStringValue) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class SchemaDefContext extends ParserRuleContext {
		public TerminalNode K_SCHEMA() { return getToken(GraphqlSchemaParser.K_SCHEMA, 0); }
		public DirectiveListContext directiveList() {
			return getRuleContext(DirectiveListContext.class,0);
		}
		public List<OperationTypeDefContext> operationTypeDef() {
			return getRuleContexts(OperationTypeDefContext.class);
		}
		public OperationTypeDefContext operationTypeDef(int i) {
			return getRuleContext(OperationTypeDefContext.class,i);
		}
		public SchemaDefContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_schemaDef; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterSchemaDef(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitSchemaDef(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitSchemaDef(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SchemaDefContext schemaDef() throws RecognitionException {
		SchemaDefContext _localctx = new SchemaDefContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_schemaDef);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(116);
			match(K_SCHEMA);
			setState(118);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__4) {
				{
				setState(117);
				directiveList();
				}
			}

			setState(120);
			match(T__0);
			setState(122); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(121);
				operationTypeDef();
				}
				}
				setState(124); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( (((_la) & ~0x3f) == 0 && ((1L << _la) & 939524096L) != 0) );
			setState(126);
			match(T__1);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class OperationTypeDefContext extends ParserRuleContext {
		public QueryOperationDefContext queryOperationDef() {
			return getRuleContext(QueryOperationDefContext.class,0);
		}
		public MutationOperationDefContext mutationOperationDef() {
			return getRuleContext(MutationOperationDefContext.class,0);
		}
		public SubscriptionOperationDefContext subscriptionOperationDef() {
			return getRuleContext(SubscriptionOperationDefContext.class,0);
		}
		public OperationTypeDefContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_operationTypeDef; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterOperationTypeDef(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitOperationTypeDef(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitOperationTypeDef(this);
			else return visitor.visitChildren(this);
		}
	}

	public final OperationTypeDefContext operationTypeDef() throws RecognitionException {
		OperationTypeDefContext _localctx = new OperationTypeDefContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_operationTypeDef);
		try {
			setState(131);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case K_QUERY:
				enterOuterAlt(_localctx, 1);
				{
				setState(128);
				queryOperationDef();
				}
				break;
			case K_MUTATION:
				enterOuterAlt(_localctx, 2);
				{
				setState(129);
				mutationOperationDef();
				}
				break;
			case K_SUBSCRIPTION:
				enterOuterAlt(_localctx, 3);
				{
				setState(130);
				subscriptionOperationDef();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class QueryOperationDefContext extends ParserRuleContext {
		public TerminalNode K_QUERY() { return getToken(GraphqlSchemaParser.K_QUERY, 0); }
		public AnyNameContext anyName() {
			return getRuleContext(AnyNameContext.class,0);
		}
		public QueryOperationDefContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_queryOperationDef; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterQueryOperationDef(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitQueryOperationDef(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitQueryOperationDef(this);
			else return visitor.visitChildren(this);
		}
	}

	public final QueryOperationDefContext queryOperationDef() throws RecognitionException {
		QueryOperationDefContext _localctx = new QueryOperationDefContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_queryOperationDef);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(133);
			match(K_QUERY);
			setState(134);
			match(T__2);
			setState(135);
			anyName();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class MutationOperationDefContext extends ParserRuleContext {
		public TerminalNode K_MUTATION() { return getToken(GraphqlSchemaParser.K_MUTATION, 0); }
		public AnyNameContext anyName() {
			return getRuleContext(AnyNameContext.class,0);
		}
		public MutationOperationDefContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_mutationOperationDef; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterMutationOperationDef(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitMutationOperationDef(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitMutationOperationDef(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MutationOperationDefContext mutationOperationDef() throws RecognitionException {
		MutationOperationDefContext _localctx = new MutationOperationDefContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_mutationOperationDef);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(137);
			match(K_MUTATION);
			setState(138);
			match(T__2);
			setState(139);
			anyName();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class SubscriptionOperationDefContext extends ParserRuleContext {
		public TerminalNode K_SUBSCRIPTION() { return getToken(GraphqlSchemaParser.K_SUBSCRIPTION, 0); }
		public AnyNameContext anyName() {
			return getRuleContext(AnyNameContext.class,0);
		}
		public SubscriptionOperationDefContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_subscriptionOperationDef; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterSubscriptionOperationDef(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitSubscriptionOperationDef(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitSubscriptionOperationDef(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SubscriptionOperationDefContext subscriptionOperationDef() throws RecognitionException {
		SubscriptionOperationDefContext _localctx = new SubscriptionOperationDefContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_subscriptionOperationDef);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(141);
			match(K_SUBSCRIPTION);
			setState(142);
			match(T__2);
			setState(143);
			anyName();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class DirectiveLocationListContext extends ParserRuleContext {
		public List<DirectiveLocationContext> directiveLocation() {
			return getRuleContexts(DirectiveLocationContext.class);
		}
		public DirectiveLocationContext directiveLocation(int i) {
			return getRuleContext(DirectiveLocationContext.class,i);
		}
		public DirectiveLocationListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_directiveLocationList; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterDirectiveLocationList(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitDirectiveLocationList(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitDirectiveLocationList(this);
			else return visitor.visitChildren(this);
		}
	}

	public final DirectiveLocationListContext directiveLocationList() throws RecognitionException {
		DirectiveLocationListContext _localctx = new DirectiveLocationListContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_directiveLocationList);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(150);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,5,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(145);
					directiveLocation();
					setState(146);
					match(T__3);
					}
					} 
				}
				setState(152);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,5,_ctx);
			}
			setState(153);
			directiveLocation();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class DirectiveLocationContext extends ParserRuleContext {
		public ExecutableDirectiveLocationContext executableDirectiveLocation() {
			return getRuleContext(ExecutableDirectiveLocationContext.class,0);
		}
		public TypeSystemDirectiveLocationContext typeSystemDirectiveLocation() {
			return getRuleContext(TypeSystemDirectiveLocationContext.class,0);
		}
		public DirectiveLocationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_directiveLocation; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterDirectiveLocation(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitDirectiveLocation(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitDirectiveLocation(this);
			else return visitor.visitChildren(this);
		}
	}

	public final DirectiveLocationContext directiveLocation() throws RecognitionException {
		DirectiveLocationContext _localctx = new DirectiveLocationContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_directiveLocation);
		try {
			setState(157);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case EXECUTABLE_DIRECTIVE_LOCATION:
				enterOuterAlt(_localctx, 1);
				{
				setState(155);
				executableDirectiveLocation();
				}
				break;
			case TYPE_SYSTEM_DIRECTIVE_LOCATION:
				enterOuterAlt(_localctx, 2);
				{
				setState(156);
				typeSystemDirectiveLocation();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ExecutableDirectiveLocationContext extends ParserRuleContext {
		public TerminalNode EXECUTABLE_DIRECTIVE_LOCATION() { return getToken(GraphqlSchemaParser.EXECUTABLE_DIRECTIVE_LOCATION, 0); }
		public ExecutableDirectiveLocationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_executableDirectiveLocation; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterExecutableDirectiveLocation(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitExecutableDirectiveLocation(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitExecutableDirectiveLocation(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ExecutableDirectiveLocationContext executableDirectiveLocation() throws RecognitionException {
		ExecutableDirectiveLocationContext _localctx = new ExecutableDirectiveLocationContext(_ctx, getState());
		enterRule(_localctx, 18, RULE_executableDirectiveLocation);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(159);
			match(EXECUTABLE_DIRECTIVE_LOCATION);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class TypeSystemDirectiveLocationContext extends ParserRuleContext {
		public TerminalNode TYPE_SYSTEM_DIRECTIVE_LOCATION() { return getToken(GraphqlSchemaParser.TYPE_SYSTEM_DIRECTIVE_LOCATION, 0); }
		public TypeSystemDirectiveLocationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_typeSystemDirectiveLocation; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterTypeSystemDirectiveLocation(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitTypeSystemDirectiveLocation(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitTypeSystemDirectiveLocation(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TypeSystemDirectiveLocationContext typeSystemDirectiveLocation() throws RecognitionException {
		TypeSystemDirectiveLocationContext _localctx = new TypeSystemDirectiveLocationContext(_ctx, getState());
		enterRule(_localctx, 20, RULE_typeSystemDirectiveLocation);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(161);
			match(TYPE_SYSTEM_DIRECTIVE_LOCATION);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class DirectiveDefContext extends ParserRuleContext {
		public TerminalNode K_DIRECTIVE() { return getToken(GraphqlSchemaParser.K_DIRECTIVE, 0); }
		public AnyNameContext anyName() {
			return getRuleContext(AnyNameContext.class,0);
		}
		public TerminalNode K_ON() { return getToken(GraphqlSchemaParser.K_ON, 0); }
		public DirectiveLocationListContext directiveLocationList() {
			return getRuleContext(DirectiveLocationListContext.class,0);
		}
		public DescriptionContext description() {
			return getRuleContext(DescriptionContext.class,0);
		}
		public ArgListContext argList() {
			return getRuleContext(ArgListContext.class,0);
		}
		public DirectiveDefContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_directiveDef; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterDirectiveDef(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitDirectiveDef(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitDirectiveDef(this);
			else return visitor.visitChildren(this);
		}
	}

	public final DirectiveDefContext directiveDef() throws RecognitionException {
		DirectiveDefContext _localctx = new DirectiveDefContext(_ctx, getState());
		enterRule(_localctx, 22, RULE_directiveDef);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(164);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==StringValue || _la==BlockStringValue) {
				{
				setState(163);
				description();
				}
			}

			setState(166);
			match(K_DIRECTIVE);
			setState(167);
			match(T__4);
			setState(168);
			anyName();
			setState(170);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__5) {
				{
				setState(169);
				argList();
				}
			}

			setState(172);
			match(K_ON);
			setState(173);
			directiveLocationList();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class DirectiveListContext extends ParserRuleContext {
		public List<DirectiveContext> directive() {
			return getRuleContexts(DirectiveContext.class);
		}
		public DirectiveContext directive(int i) {
			return getRuleContext(DirectiveContext.class,i);
		}
		public DirectiveListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_directiveList; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterDirectiveList(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitDirectiveList(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitDirectiveList(this);
			else return visitor.visitChildren(this);
		}
	}

	public final DirectiveListContext directiveList() throws RecognitionException {
		DirectiveListContext _localctx = new DirectiveListContext(_ctx, getState());
		enterRule(_localctx, 24, RULE_directiveList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(176); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(175);
				directive();
				}
				}
				setState(178); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( _la==T__4 );
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class DirectiveContext extends ParserRuleContext {
		public AnyNameContext anyName() {
			return getRuleContext(AnyNameContext.class,0);
		}
		public DirectiveArgListContext directiveArgList() {
			return getRuleContext(DirectiveArgListContext.class,0);
		}
		public DirectiveContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_directive; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterDirective(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitDirective(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitDirective(this);
			else return visitor.visitChildren(this);
		}
	}

	public final DirectiveContext directive() throws RecognitionException {
		DirectiveContext _localctx = new DirectiveContext(_ctx, getState());
		enterRule(_localctx, 26, RULE_directive);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(180);
			match(T__4);
			setState(181);
			anyName();
			setState(183);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__5) {
				{
				setState(182);
				directiveArgList();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class DirectiveArgListContext extends ParserRuleContext {
		public List<DirectiveArgContext> directiveArg() {
			return getRuleContexts(DirectiveArgContext.class);
		}
		public DirectiveArgContext directiveArg(int i) {
			return getRuleContext(DirectiveArgContext.class,i);
		}
		public DirectiveArgListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_directiveArgList; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterDirectiveArgList(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitDirectiveArgList(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitDirectiveArgList(this);
			else return visitor.visitChildren(this);
		}
	}

	public final DirectiveArgListContext directiveArgList() throws RecognitionException {
		DirectiveArgListContext _localctx = new DirectiveArgListContext(_ctx, getState());
		enterRule(_localctx, 28, RULE_directiveArgList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(185);
			match(T__5);
			setState(187); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(186);
				directiveArg();
				}
				}
				setState(189); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( (((_la) & ~0x3f) == 0 && ((1L << _la) & 34359730176L) != 0) );
			setState(191);
			match(T__6);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class DirectiveArgContext extends ParserRuleContext {
		public AnyNameContext anyName() {
			return getRuleContext(AnyNameContext.class,0);
		}
		public ValueContext value() {
			return getRuleContext(ValueContext.class,0);
		}
		public DirectiveArgContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_directiveArg; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterDirectiveArg(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitDirectiveArg(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitDirectiveArg(this);
			else return visitor.visitChildren(this);
		}
	}

	public final DirectiveArgContext directiveArg() throws RecognitionException {
		DirectiveArgContext _localctx = new DirectiveArgContext(_ctx, getState());
		enterRule(_localctx, 30, RULE_directiveArg);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(193);
			anyName();
			setState(194);
			match(T__2);
			setState(195);
			value();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class TypeDefContext extends ParserRuleContext {
		public TerminalNode K_TYPE() { return getToken(GraphqlSchemaParser.K_TYPE, 0); }
		public AnyNameContext anyName() {
			return getRuleContext(AnyNameContext.class,0);
		}
		public DescriptionContext description() {
			return getRuleContext(DescriptionContext.class,0);
		}
		public ImplementationDefContext implementationDef() {
			return getRuleContext(ImplementationDefContext.class,0);
		}
		public DirectiveListContext directiveList() {
			return getRuleContext(DirectiveListContext.class,0);
		}
		public FieldDefsContext fieldDefs() {
			return getRuleContext(FieldDefsContext.class,0);
		}
		public TypeDefContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_typeDef; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterTypeDef(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitTypeDef(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitTypeDef(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TypeDefContext typeDef() throws RecognitionException {
		TypeDefContext _localctx = new TypeDefContext(_ctx, getState());
		enterRule(_localctx, 32, RULE_typeDef);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(198);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==StringValue || _la==BlockStringValue) {
				{
				setState(197);
				description();
				}
			}

			setState(200);
			match(K_TYPE);
			setState(201);
			anyName();
			setState(203);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==K_IMPLEMENTS) {
				{
				setState(202);
				implementationDef();
				}
			}

			setState(206);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__4) {
				{
				setState(205);
				directiveList();
				}
			}

			setState(209);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__0) {
				{
				setState(208);
				fieldDefs();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class TypeExtDefContext extends ParserRuleContext {
		public TerminalNode K_EXTEND() { return getToken(GraphqlSchemaParser.K_EXTEND, 0); }
		public TerminalNode K_TYPE() { return getToken(GraphqlSchemaParser.K_TYPE, 0); }
		public AnyNameContext anyName() {
			return getRuleContext(AnyNameContext.class,0);
		}
		public DescriptionContext description() {
			return getRuleContext(DescriptionContext.class,0);
		}
		public ImplementationDefContext implementationDef() {
			return getRuleContext(ImplementationDefContext.class,0);
		}
		public DirectiveListContext directiveList() {
			return getRuleContext(DirectiveListContext.class,0);
		}
		public FieldDefsContext fieldDefs() {
			return getRuleContext(FieldDefsContext.class,0);
		}
		public TypeExtDefContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_typeExtDef; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterTypeExtDef(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitTypeExtDef(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitTypeExtDef(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TypeExtDefContext typeExtDef() throws RecognitionException {
		TypeExtDefContext _localctx = new TypeExtDefContext(_ctx, getState());
		enterRule(_localctx, 34, RULE_typeExtDef);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(212);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==StringValue || _la==BlockStringValue) {
				{
				setState(211);
				description();
				}
			}

			setState(214);
			match(K_EXTEND);
			setState(215);
			match(K_TYPE);
			setState(216);
			anyName();
			setState(218);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==K_IMPLEMENTS) {
				{
				setState(217);
				implementationDef();
				}
			}

			setState(221);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__4) {
				{
				setState(220);
				directiveList();
				}
			}

			setState(224);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__0) {
				{
				setState(223);
				fieldDefs();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class FieldDefsContext extends ParserRuleContext {
		public List<FieldDefContext> fieldDef() {
			return getRuleContexts(FieldDefContext.class);
		}
		public FieldDefContext fieldDef(int i) {
			return getRuleContext(FieldDefContext.class,i);
		}
		public FieldDefsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_fieldDefs; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterFieldDefs(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitFieldDefs(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitFieldDefs(this);
			else return visitor.visitChildren(this);
		}
	}

	public final FieldDefsContext fieldDefs() throws RecognitionException {
		FieldDefsContext _localctx = new FieldDefsContext(_ctx, getState());
		enterRule(_localctx, 36, RULE_fieldDefs);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(226);
			match(T__0);
			setState(228); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(227);
				fieldDef();
				}
				}
				setState(230); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( (((_la) & ~0x3f) == 0 && ((1L << _la) & 13228499263488L) != 0) );
			setState(232);
			match(T__1);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ImplementationDefContext extends ParserRuleContext {
		public TerminalNode K_IMPLEMENTS() { return getToken(GraphqlSchemaParser.K_IMPLEMENTS, 0); }
		public List<TerminalNode> Name() { return getTokens(GraphqlSchemaParser.Name); }
		public TerminalNode Name(int i) {
			return getToken(GraphqlSchemaParser.Name, i);
		}
		public ImplementationDefContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_implementationDef; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterImplementationDef(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitImplementationDef(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitImplementationDef(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ImplementationDefContext implementationDef() throws RecognitionException {
		ImplementationDefContext _localctx = new ImplementationDefContext(_ctx, getState());
		enterRule(_localctx, 38, RULE_implementationDef);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(234);
			match(K_IMPLEMENTS);
			setState(236);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__7) {
				{
				setState(235);
				match(T__7);
				}
			}

			setState(238);
			match(Name);
			setState(243);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__7) {
				{
				{
				setState(239);
				match(T__7);
				setState(240);
				match(Name);
				}
				}
				setState(245);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class InputTypeDefContext extends ParserRuleContext {
		public TerminalNode K_INPUT() { return getToken(GraphqlSchemaParser.K_INPUT, 0); }
		public AnyNameContext anyName() {
			return getRuleContext(AnyNameContext.class,0);
		}
		public DescriptionContext description() {
			return getRuleContext(DescriptionContext.class,0);
		}
		public DirectiveListContext directiveList() {
			return getRuleContext(DirectiveListContext.class,0);
		}
		public InputValueDefsContext inputValueDefs() {
			return getRuleContext(InputValueDefsContext.class,0);
		}
		public InputTypeDefContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_inputTypeDef; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterInputTypeDef(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitInputTypeDef(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitInputTypeDef(this);
			else return visitor.visitChildren(this);
		}
	}

	public final InputTypeDefContext inputTypeDef() throws RecognitionException {
		InputTypeDefContext _localctx = new InputTypeDefContext(_ctx, getState());
		enterRule(_localctx, 40, RULE_inputTypeDef);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(247);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==StringValue || _la==BlockStringValue) {
				{
				setState(246);
				description();
				}
			}

			setState(249);
			match(K_INPUT);
			setState(250);
			anyName();
			setState(252);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__4) {
				{
				setState(251);
				directiveList();
				}
			}

			setState(255);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__0) {
				{
				setState(254);
				inputValueDefs();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class InputTypeExtDefContext extends ParserRuleContext {
		public TerminalNode K_EXTEND() { return getToken(GraphqlSchemaParser.K_EXTEND, 0); }
		public TerminalNode K_INPUT() { return getToken(GraphqlSchemaParser.K_INPUT, 0); }
		public AnyNameContext anyName() {
			return getRuleContext(AnyNameContext.class,0);
		}
		public DescriptionContext description() {
			return getRuleContext(DescriptionContext.class,0);
		}
		public DirectiveListContext directiveList() {
			return getRuleContext(DirectiveListContext.class,0);
		}
		public InputValueDefsContext inputValueDefs() {
			return getRuleContext(InputValueDefsContext.class,0);
		}
		public InputTypeExtDefContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_inputTypeExtDef; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterInputTypeExtDef(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitInputTypeExtDef(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitInputTypeExtDef(this);
			else return visitor.visitChildren(this);
		}
	}

	public final InputTypeExtDefContext inputTypeExtDef() throws RecognitionException {
		InputTypeExtDefContext _localctx = new InputTypeExtDefContext(_ctx, getState());
		enterRule(_localctx, 42, RULE_inputTypeExtDef);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(258);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==StringValue || _la==BlockStringValue) {
				{
				setState(257);
				description();
				}
			}

			setState(260);
			match(K_EXTEND);
			setState(261);
			match(K_INPUT);
			setState(262);
			anyName();
			setState(264);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__4) {
				{
				setState(263);
				directiveList();
				}
			}

			setState(267);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__0) {
				{
				setState(266);
				inputValueDefs();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class InputValueDefsContext extends ParserRuleContext {
		public List<InputValueDefContext> inputValueDef() {
			return getRuleContexts(InputValueDefContext.class);
		}
		public InputValueDefContext inputValueDef(int i) {
			return getRuleContext(InputValueDefContext.class,i);
		}
		public InputValueDefsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_inputValueDefs; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterInputValueDefs(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitInputValueDefs(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitInputValueDefs(this);
			else return visitor.visitChildren(this);
		}
	}

	public final InputValueDefsContext inputValueDefs() throws RecognitionException {
		InputValueDefsContext _localctx = new InputValueDefsContext(_ctx, getState());
		enterRule(_localctx, 44, RULE_inputValueDefs);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(269);
			match(T__0);
			setState(271); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(270);
				inputValueDef();
				}
				}
				setState(273); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( (((_la) & ~0x3f) == 0 && ((1L << _la) & 13228499263488L) != 0) );
			setState(275);
			match(T__1);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class InputValueDefContext extends ParserRuleContext {
		public AnyNameContext anyName() {
			return getRuleContext(AnyNameContext.class,0);
		}
		public TypeSpecContext typeSpec() {
			return getRuleContext(TypeSpecContext.class,0);
		}
		public DescriptionContext description() {
			return getRuleContext(DescriptionContext.class,0);
		}
		public DefaultValueContext defaultValue() {
			return getRuleContext(DefaultValueContext.class,0);
		}
		public DirectiveListContext directiveList() {
			return getRuleContext(DirectiveListContext.class,0);
		}
		public InputValueDefContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_inputValueDef; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterInputValueDef(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitInputValueDef(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitInputValueDef(this);
			else return visitor.visitChildren(this);
		}
	}

	public final InputValueDefContext inputValueDef() throws RecognitionException {
		InputValueDefContext _localctx = new InputValueDefContext(_ctx, getState());
		enterRule(_localctx, 46, RULE_inputValueDef);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(278);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==StringValue || _la==BlockStringValue) {
				{
				setState(277);
				description();
				}
			}

			setState(280);
			anyName();
			setState(281);
			match(T__2);
			setState(282);
			typeSpec();
			setState(284);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__8) {
				{
				setState(283);
				defaultValue();
				}
			}

			setState(287);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__4) {
				{
				setState(286);
				directiveList();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class InterfaceDefContext extends ParserRuleContext {
		public TerminalNode K_INTERFACE() { return getToken(GraphqlSchemaParser.K_INTERFACE, 0); }
		public AnyNameContext anyName() {
			return getRuleContext(AnyNameContext.class,0);
		}
		public DescriptionContext description() {
			return getRuleContext(DescriptionContext.class,0);
		}
		public DirectiveListContext directiveList() {
			return getRuleContext(DirectiveListContext.class,0);
		}
		public FieldDefsContext fieldDefs() {
			return getRuleContext(FieldDefsContext.class,0);
		}
		public InterfaceDefContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_interfaceDef; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterInterfaceDef(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitInterfaceDef(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitInterfaceDef(this);
			else return visitor.visitChildren(this);
		}
	}

	public final InterfaceDefContext interfaceDef() throws RecognitionException {
		InterfaceDefContext _localctx = new InterfaceDefContext(_ctx, getState());
		enterRule(_localctx, 48, RULE_interfaceDef);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(290);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==StringValue || _la==BlockStringValue) {
				{
				setState(289);
				description();
				}
			}

			setState(292);
			match(K_INTERFACE);
			setState(293);
			anyName();
			setState(295);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__4) {
				{
				setState(294);
				directiveList();
				}
			}

			setState(298);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__0) {
				{
				setState(297);
				fieldDefs();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ScalarDefContext extends ParserRuleContext {
		public TerminalNode K_SCALAR() { return getToken(GraphqlSchemaParser.K_SCALAR, 0); }
		public AnyNameContext anyName() {
			return getRuleContext(AnyNameContext.class,0);
		}
		public DescriptionContext description() {
			return getRuleContext(DescriptionContext.class,0);
		}
		public DirectiveListContext directiveList() {
			return getRuleContext(DirectiveListContext.class,0);
		}
		public ScalarDefContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_scalarDef; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterScalarDef(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitScalarDef(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitScalarDef(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ScalarDefContext scalarDef() throws RecognitionException {
		ScalarDefContext _localctx = new ScalarDefContext(_ctx, getState());
		enterRule(_localctx, 50, RULE_scalarDef);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(301);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==StringValue || _la==BlockStringValue) {
				{
				setState(300);
				description();
				}
			}

			setState(303);
			match(K_SCALAR);
			setState(304);
			anyName();
			setState(306);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__4) {
				{
				setState(305);
				directiveList();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class UnionDefContext extends ParserRuleContext {
		public TerminalNode K_UNION() { return getToken(GraphqlSchemaParser.K_UNION, 0); }
		public AnyNameContext anyName() {
			return getRuleContext(AnyNameContext.class,0);
		}
		public UnionTypesContext unionTypes() {
			return getRuleContext(UnionTypesContext.class,0);
		}
		public DescriptionContext description() {
			return getRuleContext(DescriptionContext.class,0);
		}
		public DirectiveListContext directiveList() {
			return getRuleContext(DirectiveListContext.class,0);
		}
		public UnionDefContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_unionDef; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterUnionDef(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitUnionDef(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitUnionDef(this);
			else return visitor.visitChildren(this);
		}
	}

	public final UnionDefContext unionDef() throws RecognitionException {
		UnionDefContext _localctx = new UnionDefContext(_ctx, getState());
		enterRule(_localctx, 52, RULE_unionDef);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(309);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==StringValue || _la==BlockStringValue) {
				{
				setState(308);
				description();
				}
			}

			setState(311);
			match(K_UNION);
			setState(312);
			anyName();
			setState(314);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__4) {
				{
				setState(313);
				directiveList();
				}
			}

			setState(316);
			match(T__8);
			setState(317);
			unionTypes();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class UnionExtDefContext extends ParserRuleContext {
		public TerminalNode K_EXTEND() { return getToken(GraphqlSchemaParser.K_EXTEND, 0); }
		public TerminalNode K_UNION() { return getToken(GraphqlSchemaParser.K_UNION, 0); }
		public AnyNameContext anyName() {
			return getRuleContext(AnyNameContext.class,0);
		}
		public UnionTypesContext unionTypes() {
			return getRuleContext(UnionTypesContext.class,0);
		}
		public DescriptionContext description() {
			return getRuleContext(DescriptionContext.class,0);
		}
		public DirectiveListContext directiveList() {
			return getRuleContext(DirectiveListContext.class,0);
		}
		public UnionExtDefContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_unionExtDef; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterUnionExtDef(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitUnionExtDef(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitUnionExtDef(this);
			else return visitor.visitChildren(this);
		}
	}

	public final UnionExtDefContext unionExtDef() throws RecognitionException {
		UnionExtDefContext _localctx = new UnionExtDefContext(_ctx, getState());
		enterRule(_localctx, 54, RULE_unionExtDef);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(320);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==StringValue || _la==BlockStringValue) {
				{
				setState(319);
				description();
				}
			}

			setState(322);
			match(K_EXTEND);
			setState(323);
			match(K_UNION);
			setState(324);
			anyName();
			setState(326);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__4) {
				{
				setState(325);
				directiveList();
				}
			}

			setState(328);
			match(T__8);
			setState(329);
			unionTypes();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class UnionTypesContext extends ParserRuleContext {
		public List<AnyNameContext> anyName() {
			return getRuleContexts(AnyNameContext.class);
		}
		public AnyNameContext anyName(int i) {
			return getRuleContext(AnyNameContext.class,i);
		}
		public UnionTypesContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_unionTypes; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterUnionTypes(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitUnionTypes(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitUnionTypes(this);
			else return visitor.visitChildren(this);
		}
	}

	public final UnionTypesContext unionTypes() throws RecognitionException {
		UnionTypesContext _localctx = new UnionTypesContext(_ctx, getState());
		enterRule(_localctx, 56, RULE_unionTypes);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(336);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,42,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(331);
					anyName();
					setState(332);
					match(T__3);
					}
					} 
				}
				setState(338);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,42,_ctx);
			}
			setState(339);
			anyName();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class EnumDefContext extends ParserRuleContext {
		public TerminalNode K_ENUM() { return getToken(GraphqlSchemaParser.K_ENUM, 0); }
		public AnyNameContext anyName() {
			return getRuleContext(AnyNameContext.class,0);
		}
		public EnumValueDefsContext enumValueDefs() {
			return getRuleContext(EnumValueDefsContext.class,0);
		}
		public DescriptionContext description() {
			return getRuleContext(DescriptionContext.class,0);
		}
		public DirectiveListContext directiveList() {
			return getRuleContext(DirectiveListContext.class,0);
		}
		public EnumDefContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_enumDef; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterEnumDef(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitEnumDef(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitEnumDef(this);
			else return visitor.visitChildren(this);
		}
	}

	public final EnumDefContext enumDef() throws RecognitionException {
		EnumDefContext _localctx = new EnumDefContext(_ctx, getState());
		enterRule(_localctx, 58, RULE_enumDef);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(342);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==StringValue || _la==BlockStringValue) {
				{
				setState(341);
				description();
				}
			}

			setState(344);
			match(K_ENUM);
			setState(345);
			anyName();
			setState(347);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__4) {
				{
				setState(346);
				directiveList();
				}
			}

			setState(349);
			enumValueDefs();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class EnumValueDefsContext extends ParserRuleContext {
		public List<EnumValueDefContext> enumValueDef() {
			return getRuleContexts(EnumValueDefContext.class);
		}
		public EnumValueDefContext enumValueDef(int i) {
			return getRuleContext(EnumValueDefContext.class,i);
		}
		public EnumValueDefsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_enumValueDefs; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterEnumValueDefs(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitEnumValueDefs(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitEnumValueDefs(this);
			else return visitor.visitChildren(this);
		}
	}

	public final EnumValueDefsContext enumValueDefs() throws RecognitionException {
		EnumValueDefsContext _localctx = new EnumValueDefsContext(_ctx, getState());
		enterRule(_localctx, 60, RULE_enumValueDefs);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(351);
			match(T__0);
			setState(353); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(352);
				enumValueDef();
				}
				}
				setState(355); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( (((_la) & ~0x3f) == 0 && ((1L << _la) & 13213466877952L) != 0) );
			setState(357);
			match(T__1);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class EnumValueDefContext extends ParserRuleContext {
		public NameTokensContext nameTokens() {
			return getRuleContext(NameTokensContext.class,0);
		}
		public DescriptionContext description() {
			return getRuleContext(DescriptionContext.class,0);
		}
		public DirectiveListContext directiveList() {
			return getRuleContext(DirectiveListContext.class,0);
		}
		public EnumValueDefContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_enumValueDef; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterEnumValueDef(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitEnumValueDef(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitEnumValueDef(this);
			else return visitor.visitChildren(this);
		}
	}

	public final EnumValueDefContext enumValueDef() throws RecognitionException {
		EnumValueDefContext _localctx = new EnumValueDefContext(_ctx, getState());
		enterRule(_localctx, 62, RULE_enumValueDef);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(360);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==StringValue || _la==BlockStringValue) {
				{
				setState(359);
				description();
				}
			}

			setState(362);
			nameTokens();
			setState(364);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__4) {
				{
				setState(363);
				directiveList();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class FieldDefContext extends ParserRuleContext {
		public AnyNameContext anyName() {
			return getRuleContext(AnyNameContext.class,0);
		}
		public TypeSpecContext typeSpec() {
			return getRuleContext(TypeSpecContext.class,0);
		}
		public DescriptionContext description() {
			return getRuleContext(DescriptionContext.class,0);
		}
		public ArgListContext argList() {
			return getRuleContext(ArgListContext.class,0);
		}
		public DirectiveListContext directiveList() {
			return getRuleContext(DirectiveListContext.class,0);
		}
		public FieldDefContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_fieldDef; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterFieldDef(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitFieldDef(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitFieldDef(this);
			else return visitor.visitChildren(this);
		}
	}

	public final FieldDefContext fieldDef() throws RecognitionException {
		FieldDefContext _localctx = new FieldDefContext(_ctx, getState());
		enterRule(_localctx, 64, RULE_fieldDef);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(367);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==StringValue || _la==BlockStringValue) {
				{
				setState(366);
				description();
				}
			}

			setState(369);
			anyName();
			setState(371);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__5) {
				{
				setState(370);
				argList();
				}
			}

			setState(373);
			match(T__2);
			setState(374);
			typeSpec();
			setState(376);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__4) {
				{
				setState(375);
				directiveList();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ArgListContext extends ParserRuleContext {
		public List<ArgumentContext> argument() {
			return getRuleContexts(ArgumentContext.class);
		}
		public ArgumentContext argument(int i) {
			return getRuleContext(ArgumentContext.class,i);
		}
		public ArgListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_argList; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterArgList(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitArgList(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitArgList(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ArgListContext argList() throws RecognitionException {
		ArgListContext _localctx = new ArgListContext(_ctx, getState());
		enterRule(_localctx, 66, RULE_argList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(378);
			match(T__5);
			setState(380); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(379);
				argument();
				}
				}
				setState(382); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( (((_la) & ~0x3f) == 0 && ((1L << _la) & 13228499263488L) != 0) );
			setState(384);
			match(T__6);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ArgumentContext extends ParserRuleContext {
		public AnyNameContext anyName() {
			return getRuleContext(AnyNameContext.class,0);
		}
		public TypeSpecContext typeSpec() {
			return getRuleContext(TypeSpecContext.class,0);
		}
		public DescriptionContext description() {
			return getRuleContext(DescriptionContext.class,0);
		}
		public DefaultValueContext defaultValue() {
			return getRuleContext(DefaultValueContext.class,0);
		}
		public DirectiveListContext directiveList() {
			return getRuleContext(DirectiveListContext.class,0);
		}
		public ArgumentContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_argument; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterArgument(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitArgument(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitArgument(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ArgumentContext argument() throws RecognitionException {
		ArgumentContext _localctx = new ArgumentContext(_ctx, getState());
		enterRule(_localctx, 68, RULE_argument);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(387);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==StringValue || _la==BlockStringValue) {
				{
				setState(386);
				description();
				}
			}

			setState(389);
			anyName();
			setState(390);
			match(T__2);
			setState(391);
			typeSpec();
			setState(393);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__8) {
				{
				setState(392);
				defaultValue();
				}
			}

			setState(396);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__4) {
				{
				setState(395);
				directiveList();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class TypeSpecContext extends ParserRuleContext {
		public TypeNameContext typeName() {
			return getRuleContext(TypeNameContext.class,0);
		}
		public ListTypeContext listType() {
			return getRuleContext(ListTypeContext.class,0);
		}
		public RequiredContext required() {
			return getRuleContext(RequiredContext.class,0);
		}
		public TypeSpecContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_typeSpec; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterTypeSpec(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitTypeSpec(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitTypeSpec(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TypeSpecContext typeSpec() throws RecognitionException {
		TypeSpecContext _localctx = new TypeSpecContext(_ctx, getState());
		enterRule(_localctx, 70, RULE_typeSpec);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(400);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case EXECUTABLE_DIRECTIVE_LOCATION:
			case TYPE_SYSTEM_DIRECTIVE_LOCATION:
			case K_TYPE:
			case K_IMPLEMENTS:
			case K_INTERFACE:
			case K_SCHEMA:
			case K_ENUM:
			case K_UNION:
			case K_INPUT:
			case K_DIRECTIVE:
			case K_EXTEND:
			case K_SCALAR:
			case K_ON:
			case K_FRAGMENT:
			case K_QUERY:
			case K_MUTATION:
			case K_SUBSCRIPTION:
			case K_VALUE:
			case K_TRUE:
			case K_FALSE:
			case K_NULL:
			case Name:
				{
				setState(398);
				typeName();
				}
				break;
			case T__9:
				{
				setState(399);
				listType();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(403);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__11) {
				{
				setState(402);
				required();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class TypeNameContext extends ParserRuleContext {
		public AnyNameContext anyName() {
			return getRuleContext(AnyNameContext.class,0);
		}
		public TypeNameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_typeName; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterTypeName(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitTypeName(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitTypeName(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TypeNameContext typeName() throws RecognitionException {
		TypeNameContext _localctx = new TypeNameContext(_ctx, getState());
		enterRule(_localctx, 72, RULE_typeName);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(405);
			anyName();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ListTypeContext extends ParserRuleContext {
		public TypeSpecContext typeSpec() {
			return getRuleContext(TypeSpecContext.class,0);
		}
		public ListTypeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_listType; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterListType(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitListType(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitListType(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ListTypeContext listType() throws RecognitionException {
		ListTypeContext _localctx = new ListTypeContext(_ctx, getState());
		enterRule(_localctx, 74, RULE_listType);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(407);
			match(T__9);
			setState(408);
			typeSpec();
			setState(409);
			match(T__10);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class RequiredContext extends ParserRuleContext {
		public RequiredContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_required; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterRequired(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitRequired(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitRequired(this);
			else return visitor.visitChildren(this);
		}
	}

	public final RequiredContext required() throws RecognitionException {
		RequiredContext _localctx = new RequiredContext(_ctx, getState());
		enterRule(_localctx, 76, RULE_required);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(411);
			match(T__11);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class DefaultValueContext extends ParserRuleContext {
		public ValueContext value() {
			return getRuleContext(ValueContext.class,0);
		}
		public DefaultValueContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_defaultValue; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterDefaultValue(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitDefaultValue(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitDefaultValue(this);
			else return visitor.visitChildren(this);
		}
	}

	public final DefaultValueContext defaultValue() throws RecognitionException {
		DefaultValueContext _localctx = new DefaultValueContext(_ctx, getState());
		enterRule(_localctx, 78, RULE_defaultValue);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(413);
			match(T__8);
			setState(414);
			value();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class AnyNameContext extends ParserRuleContext {
		public NameTokensContext nameTokens() {
			return getRuleContext(NameTokensContext.class,0);
		}
		public TerminalNode K_TRUE() { return getToken(GraphqlSchemaParser.K_TRUE, 0); }
		public TerminalNode K_FALSE() { return getToken(GraphqlSchemaParser.K_FALSE, 0); }
		public TerminalNode K_NULL() { return getToken(GraphqlSchemaParser.K_NULL, 0); }
		public AnyNameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_anyName; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterAnyName(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitAnyName(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitAnyName(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AnyNameContext anyName() throws RecognitionException {
		AnyNameContext _localctx = new AnyNameContext(_ctx, getState());
		enterRule(_localctx, 80, RULE_anyName);
		try {
			setState(420);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case EXECUTABLE_DIRECTIVE_LOCATION:
			case TYPE_SYSTEM_DIRECTIVE_LOCATION:
			case K_TYPE:
			case K_IMPLEMENTS:
			case K_INTERFACE:
			case K_SCHEMA:
			case K_ENUM:
			case K_UNION:
			case K_INPUT:
			case K_DIRECTIVE:
			case K_EXTEND:
			case K_SCALAR:
			case K_ON:
			case K_FRAGMENT:
			case K_QUERY:
			case K_MUTATION:
			case K_SUBSCRIPTION:
			case K_VALUE:
			case Name:
				enterOuterAlt(_localctx, 1);
				{
				setState(416);
				nameTokens();
				}
				break;
			case K_TRUE:
				enterOuterAlt(_localctx, 2);
				{
				setState(417);
				match(K_TRUE);
				}
				break;
			case K_FALSE:
				enterOuterAlt(_localctx, 3);
				{
				setState(418);
				match(K_FALSE);
				}
				break;
			case K_NULL:
				enterOuterAlt(_localctx, 4);
				{
				setState(419);
				match(K_NULL);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class NameTokensContext extends ParserRuleContext {
		public TerminalNode Name() { return getToken(GraphqlSchemaParser.Name, 0); }
		public TerminalNode EXECUTABLE_DIRECTIVE_LOCATION() { return getToken(GraphqlSchemaParser.EXECUTABLE_DIRECTIVE_LOCATION, 0); }
		public TerminalNode TYPE_SYSTEM_DIRECTIVE_LOCATION() { return getToken(GraphqlSchemaParser.TYPE_SYSTEM_DIRECTIVE_LOCATION, 0); }
		public TerminalNode K_TYPE() { return getToken(GraphqlSchemaParser.K_TYPE, 0); }
		public TerminalNode K_IMPLEMENTS() { return getToken(GraphqlSchemaParser.K_IMPLEMENTS, 0); }
		public TerminalNode K_INTERFACE() { return getToken(GraphqlSchemaParser.K_INTERFACE, 0); }
		public TerminalNode K_SCHEMA() { return getToken(GraphqlSchemaParser.K_SCHEMA, 0); }
		public TerminalNode K_ENUM() { return getToken(GraphqlSchemaParser.K_ENUM, 0); }
		public TerminalNode K_UNION() { return getToken(GraphqlSchemaParser.K_UNION, 0); }
		public TerminalNode K_INPUT() { return getToken(GraphqlSchemaParser.K_INPUT, 0); }
		public TerminalNode K_DIRECTIVE() { return getToken(GraphqlSchemaParser.K_DIRECTIVE, 0); }
		public TerminalNode K_EXTEND() { return getToken(GraphqlSchemaParser.K_EXTEND, 0); }
		public TerminalNode K_SCALAR() { return getToken(GraphqlSchemaParser.K_SCALAR, 0); }
		public TerminalNode K_ON() { return getToken(GraphqlSchemaParser.K_ON, 0); }
		public TerminalNode K_FRAGMENT() { return getToken(GraphqlSchemaParser.K_FRAGMENT, 0); }
		public TerminalNode K_QUERY() { return getToken(GraphqlSchemaParser.K_QUERY, 0); }
		public TerminalNode K_MUTATION() { return getToken(GraphqlSchemaParser.K_MUTATION, 0); }
		public TerminalNode K_SUBSCRIPTION() { return getToken(GraphqlSchemaParser.K_SUBSCRIPTION, 0); }
		public TerminalNode K_VALUE() { return getToken(GraphqlSchemaParser.K_VALUE, 0); }
		public NameTokensContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_nameTokens; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterNameTokens(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitNameTokens(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitNameTokens(this);
			else return visitor.visitChildren(this);
		}
	}

	public final NameTokensContext nameTokens() throws RecognitionException {
		NameTokensContext _localctx = new NameTokensContext(_ctx, getState());
		enterRule(_localctx, 82, RULE_nameTokens);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(422);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 19327344640L) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class BooleanValueContext extends ParserRuleContext {
		public TerminalNode K_TRUE() { return getToken(GraphqlSchemaParser.K_TRUE, 0); }
		public TerminalNode K_FALSE() { return getToken(GraphqlSchemaParser.K_FALSE, 0); }
		public BooleanValueContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_booleanValue; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterBooleanValue(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitBooleanValue(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitBooleanValue(this);
			else return visitor.visitChildren(this);
		}
	}

	public final BooleanValueContext booleanValue() throws RecognitionException {
		BooleanValueContext _localctx = new BooleanValueContext(_ctx, getState());
		enterRule(_localctx, 84, RULE_booleanValue);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(424);
			_la = _input.LA(1);
			if ( !(_la==K_TRUE || _la==K_FALSE) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ValueContext extends ParserRuleContext {
		public TerminalNode IntValue() { return getToken(GraphqlSchemaParser.IntValue, 0); }
		public TerminalNode FloatValue() { return getToken(GraphqlSchemaParser.FloatValue, 0); }
		public TerminalNode StringValue() { return getToken(GraphqlSchemaParser.StringValue, 0); }
		public TerminalNode BlockStringValue() { return getToken(GraphqlSchemaParser.BlockStringValue, 0); }
		public BooleanValueContext booleanValue() {
			return getRuleContext(BooleanValueContext.class,0);
		}
		public NullValueContext nullValue() {
			return getRuleContext(NullValueContext.class,0);
		}
		public EnumValueContext enumValue() {
			return getRuleContext(EnumValueContext.class,0);
		}
		public ArrayValueContext arrayValue() {
			return getRuleContext(ArrayValueContext.class,0);
		}
		public ObjectValueContext objectValue() {
			return getRuleContext(ObjectValueContext.class,0);
		}
		public ValueContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_value; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterValue(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitValue(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitValue(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ValueContext value() throws RecognitionException {
		ValueContext _localctx = new ValueContext(_ctx, getState());
		enterRule(_localctx, 86, RULE_value);
		try {
			setState(435);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case IntValue:
				enterOuterAlt(_localctx, 1);
				{
				setState(426);
				match(IntValue);
				}
				break;
			case FloatValue:
				enterOuterAlt(_localctx, 2);
				{
				setState(427);
				match(FloatValue);
				}
				break;
			case StringValue:
				enterOuterAlt(_localctx, 3);
				{
				setState(428);
				match(StringValue);
				}
				break;
			case BlockStringValue:
				enterOuterAlt(_localctx, 4);
				{
				setState(429);
				match(BlockStringValue);
				}
				break;
			case K_TRUE:
			case K_FALSE:
				enterOuterAlt(_localctx, 5);
				{
				setState(430);
				booleanValue();
				}
				break;
			case K_NULL:
				enterOuterAlt(_localctx, 6);
				{
				setState(431);
				nullValue();
				}
				break;
			case EXECUTABLE_DIRECTIVE_LOCATION:
			case TYPE_SYSTEM_DIRECTIVE_LOCATION:
			case K_TYPE:
			case K_IMPLEMENTS:
			case K_INTERFACE:
			case K_SCHEMA:
			case K_ENUM:
			case K_UNION:
			case K_INPUT:
			case K_DIRECTIVE:
			case K_EXTEND:
			case K_SCALAR:
			case K_ON:
			case K_FRAGMENT:
			case K_QUERY:
			case K_MUTATION:
			case K_SUBSCRIPTION:
			case K_VALUE:
			case Name:
				enterOuterAlt(_localctx, 7);
				{
				setState(432);
				enumValue();
				}
				break;
			case T__9:
				enterOuterAlt(_localctx, 8);
				{
				setState(433);
				arrayValue();
				}
				break;
			case T__0:
				enterOuterAlt(_localctx, 9);
				{
				setState(434);
				objectValue();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class EnumValueContext extends ParserRuleContext {
		public NameTokensContext nameTokens() {
			return getRuleContext(NameTokensContext.class,0);
		}
		public EnumValueContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_enumValue; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterEnumValue(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitEnumValue(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitEnumValue(this);
			else return visitor.visitChildren(this);
		}
	}

	public final EnumValueContext enumValue() throws RecognitionException {
		EnumValueContext _localctx = new EnumValueContext(_ctx, getState());
		enterRule(_localctx, 88, RULE_enumValue);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(437);
			nameTokens();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ArrayValueContext extends ParserRuleContext {
		public List<ValueContext> value() {
			return getRuleContexts(ValueContext.class);
		}
		public ValueContext value(int i) {
			return getRuleContext(ValueContext.class,i);
		}
		public ArrayValueContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arrayValue; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterArrayValue(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitArrayValue(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitArrayValue(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ArrayValueContext arrayValue() throws RecognitionException {
		ArrayValueContext _localctx = new ArrayValueContext(_ctx, getState());
		enterRule(_localctx, 90, RULE_arrayValue);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(439);
			match(T__9);
			setState(443);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 13331578479618L) != 0)) {
				{
				{
				setState(440);
				value();
				}
				}
				setState(445);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(446);
			match(T__10);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ObjectValueContext extends ParserRuleContext {
		public List<ObjectFieldContext> objectField() {
			return getRuleContexts(ObjectFieldContext.class);
		}
		public ObjectFieldContext objectField(int i) {
			return getRuleContext(ObjectFieldContext.class,i);
		}
		public ObjectValueContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_objectValue; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterObjectValue(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitObjectValue(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitObjectValue(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ObjectValueContext objectValue() throws RecognitionException {
		ObjectValueContext _localctx = new ObjectValueContext(_ctx, getState());
		enterRule(_localctx, 92, RULE_objectValue);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(448);
			match(T__0);
			setState(452);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 34359730176L) != 0)) {
				{
				{
				setState(449);
				objectField();
				}
				}
				setState(454);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(455);
			match(T__1);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ObjectFieldContext extends ParserRuleContext {
		public AnyNameContext anyName() {
			return getRuleContext(AnyNameContext.class,0);
		}
		public ValueContext value() {
			return getRuleContext(ValueContext.class,0);
		}
		public ObjectFieldContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_objectField; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterObjectField(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitObjectField(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitObjectField(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ObjectFieldContext objectField() throws RecognitionException {
		ObjectFieldContext _localctx = new ObjectFieldContext(_ctx, getState());
		enterRule(_localctx, 94, RULE_objectField);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(457);
			anyName();
			setState(458);
			match(T__2);
			setState(459);
			value();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class NullValueContext extends ParserRuleContext {
		public TerminalNode K_NULL() { return getToken(GraphqlSchemaParser.K_NULL, 0); }
		public NullValueContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_nullValue; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).enterNullValue(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof GraphqlSchemaListener ) ((GraphqlSchemaListener)listener).exitNullValue(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof GraphqlSchemaVisitor ) return ((GraphqlSchemaVisitor<? extends T>)visitor).visitNullValue(this);
			else return visitor.visitChildren(this);
		}
	}

	public final NullValueContext nullValue() throws RecognitionException {
		NullValueContext _localctx = new NullValueContext(_ctx, getState());
		enterRule(_localctx, 96, RULE_nullValue);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(461);
			match(K_NULL);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static final String _serializedATN =
		"\u0004\u0001,\u01d0\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
		"\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002"+
		"\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007\u0002"+
		"\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002\u000b\u0007\u000b\u0002"+
		"\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e\u0002\u000f\u0007\u000f"+
		"\u0002\u0010\u0007\u0010\u0002\u0011\u0007\u0011\u0002\u0012\u0007\u0012"+
		"\u0002\u0013\u0007\u0013\u0002\u0014\u0007\u0014\u0002\u0015\u0007\u0015"+
		"\u0002\u0016\u0007\u0016\u0002\u0017\u0007\u0017\u0002\u0018\u0007\u0018"+
		"\u0002\u0019\u0007\u0019\u0002\u001a\u0007\u001a\u0002\u001b\u0007\u001b"+
		"\u0002\u001c\u0007\u001c\u0002\u001d\u0007\u001d\u0002\u001e\u0007\u001e"+
		"\u0002\u001f\u0007\u001f\u0002 \u0007 \u0002!\u0007!\u0002\"\u0007\"\u0002"+
		"#\u0007#\u0002$\u0007$\u0002%\u0007%\u0002&\u0007&\u0002\'\u0007\'\u0002"+
		"(\u0007(\u0002)\u0007)\u0002*\u0007*\u0002+\u0007+\u0002,\u0007,\u0002"+
		"-\u0007-\u0002.\u0007.\u0002/\u0007/\u00020\u00070\u0001\u0000\u0001\u0000"+
		"\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000"+
		"\u0001\u0000\u0001\u0000\u0001\u0000\u0005\u0000n\b\u0000\n\u0000\f\u0000"+
		"q\t\u0000\u0001\u0001\u0001\u0001\u0001\u0002\u0001\u0002\u0003\u0002"+
		"w\b\u0002\u0001\u0002\u0001\u0002\u0004\u0002{\b\u0002\u000b\u0002\f\u0002"+
		"|\u0001\u0002\u0001\u0002\u0001\u0003\u0001\u0003\u0001\u0003\u0003\u0003"+
		"\u0084\b\u0003\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0005"+
		"\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0006\u0001\u0006\u0001\u0006"+
		"\u0001\u0006\u0001\u0007\u0001\u0007\u0001\u0007\u0005\u0007\u0095\b\u0007"+
		"\n\u0007\f\u0007\u0098\t\u0007\u0001\u0007\u0001\u0007\u0001\b\u0001\b"+
		"\u0003\b\u009e\b\b\u0001\t\u0001\t\u0001\n\u0001\n\u0001\u000b\u0003\u000b"+
		"\u00a5\b\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0003\u000b"+
		"\u00ab\b\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\f\u0004\f\u00b1"+
		"\b\f\u000b\f\f\f\u00b2\u0001\r\u0001\r\u0001\r\u0003\r\u00b8\b\r\u0001"+
		"\u000e\u0001\u000e\u0004\u000e\u00bc\b\u000e\u000b\u000e\f\u000e\u00bd"+
		"\u0001\u000e\u0001\u000e\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u000f"+
		"\u0001\u0010\u0003\u0010\u00c7\b\u0010\u0001\u0010\u0001\u0010\u0001\u0010"+
		"\u0003\u0010\u00cc\b\u0010\u0001\u0010\u0003\u0010\u00cf\b\u0010\u0001"+
		"\u0010\u0003\u0010\u00d2\b\u0010\u0001\u0011\u0003\u0011\u00d5\b\u0011"+
		"\u0001\u0011\u0001\u0011\u0001\u0011\u0001\u0011\u0003\u0011\u00db\b\u0011"+
		"\u0001\u0011\u0003\u0011\u00de\b\u0011\u0001\u0011\u0003\u0011\u00e1\b"+
		"\u0011\u0001\u0012\u0001\u0012\u0004\u0012\u00e5\b\u0012\u000b\u0012\f"+
		"\u0012\u00e6\u0001\u0012\u0001\u0012\u0001\u0013\u0001\u0013\u0003\u0013"+
		"\u00ed\b\u0013\u0001\u0013\u0001\u0013\u0001\u0013\u0005\u0013\u00f2\b"+
		"\u0013\n\u0013\f\u0013\u00f5\t\u0013\u0001\u0014\u0003\u0014\u00f8\b\u0014"+
		"\u0001\u0014\u0001\u0014\u0001\u0014\u0003\u0014\u00fd\b\u0014\u0001\u0014"+
		"\u0003\u0014\u0100\b\u0014\u0001\u0015\u0003\u0015\u0103\b\u0015\u0001"+
		"\u0015\u0001\u0015\u0001\u0015\u0001\u0015\u0003\u0015\u0109\b\u0015\u0001"+
		"\u0015\u0003\u0015\u010c\b\u0015\u0001\u0016\u0001\u0016\u0004\u0016\u0110"+
		"\b\u0016\u000b\u0016\f\u0016\u0111\u0001\u0016\u0001\u0016\u0001\u0017"+
		"\u0003\u0017\u0117\b\u0017\u0001\u0017\u0001\u0017\u0001\u0017\u0001\u0017"+
		"\u0003\u0017\u011d\b\u0017\u0001\u0017\u0003\u0017\u0120\b\u0017\u0001"+
		"\u0018\u0003\u0018\u0123\b\u0018\u0001\u0018\u0001\u0018\u0001\u0018\u0003"+
		"\u0018\u0128\b\u0018\u0001\u0018\u0003\u0018\u012b\b\u0018\u0001\u0019"+
		"\u0003\u0019\u012e\b\u0019\u0001\u0019\u0001\u0019\u0001\u0019\u0003\u0019"+
		"\u0133\b\u0019\u0001\u001a\u0003\u001a\u0136\b\u001a\u0001\u001a\u0001"+
		"\u001a\u0001\u001a\u0003\u001a\u013b\b\u001a\u0001\u001a\u0001\u001a\u0001"+
		"\u001a\u0001\u001b\u0003\u001b\u0141\b\u001b\u0001\u001b\u0001\u001b\u0001"+
		"\u001b\u0001\u001b\u0003\u001b\u0147\b\u001b\u0001\u001b\u0001\u001b\u0001"+
		"\u001b\u0001\u001c\u0001\u001c\u0001\u001c\u0005\u001c\u014f\b\u001c\n"+
		"\u001c\f\u001c\u0152\t\u001c\u0001\u001c\u0001\u001c\u0001\u001d\u0003"+
		"\u001d\u0157\b\u001d\u0001\u001d\u0001\u001d\u0001\u001d\u0003\u001d\u015c"+
		"\b\u001d\u0001\u001d\u0001\u001d\u0001\u001e\u0001\u001e\u0004\u001e\u0162"+
		"\b\u001e\u000b\u001e\f\u001e\u0163\u0001\u001e\u0001\u001e\u0001\u001f"+
		"\u0003\u001f\u0169\b\u001f\u0001\u001f\u0001\u001f\u0003\u001f\u016d\b"+
		"\u001f\u0001 \u0003 \u0170\b \u0001 \u0001 \u0003 \u0174\b \u0001 \u0001"+
		" \u0001 \u0003 \u0179\b \u0001!\u0001!\u0004!\u017d\b!\u000b!\f!\u017e"+
		"\u0001!\u0001!\u0001\"\u0003\"\u0184\b\"\u0001\"\u0001\"\u0001\"\u0001"+
		"\"\u0003\"\u018a\b\"\u0001\"\u0003\"\u018d\b\"\u0001#\u0001#\u0003#\u0191"+
		"\b#\u0001#\u0003#\u0194\b#\u0001$\u0001$\u0001%\u0001%\u0001%\u0001%\u0001"+
		"&\u0001&\u0001\'\u0001\'\u0001\'\u0001(\u0001(\u0001(\u0001(\u0003(\u01a5"+
		"\b(\u0001)\u0001)\u0001*\u0001*\u0001+\u0001+\u0001+\u0001+\u0001+\u0001"+
		"+\u0001+\u0001+\u0001+\u0003+\u01b4\b+\u0001,\u0001,\u0001-\u0001-\u0005"+
		"-\u01ba\b-\n-\f-\u01bd\t-\u0001-\u0001-\u0001.\u0001.\u0005.\u01c3\b."+
		"\n.\f.\u01c6\t.\u0001.\u0001.\u0001/\u0001/\u0001/\u0001/\u00010\u0001"+
		"0\u00010\u0000\u00001\u0000\u0002\u0004\u0006\b\n\f\u000e\u0010\u0012"+
		"\u0014\u0016\u0018\u001a\u001c\u001e \"$&(*,.02468:<>@BDFHJLNPRTVXZ\\"+
		"^`\u0000\u0003\u0001\u0000*+\u0002\u0000\r\u001e\"\"\u0001\u0000\u001f"+
		" \u01ee\u0000o\u0001\u0000\u0000\u0000\u0002r\u0001\u0000\u0000\u0000"+
		"\u0004t\u0001\u0000\u0000\u0000\u0006\u0083\u0001\u0000\u0000\u0000\b"+
		"\u0085\u0001\u0000\u0000\u0000\n\u0089\u0001\u0000\u0000\u0000\f\u008d"+
		"\u0001\u0000\u0000\u0000\u000e\u0096\u0001\u0000\u0000\u0000\u0010\u009d"+
		"\u0001\u0000\u0000\u0000\u0012\u009f\u0001\u0000\u0000\u0000\u0014\u00a1"+
		"\u0001\u0000\u0000\u0000\u0016\u00a4\u0001\u0000\u0000\u0000\u0018\u00b0"+
		"\u0001\u0000\u0000\u0000\u001a\u00b4\u0001\u0000\u0000\u0000\u001c\u00b9"+
		"\u0001\u0000\u0000\u0000\u001e\u00c1\u0001\u0000\u0000\u0000 \u00c6\u0001"+
		"\u0000\u0000\u0000\"\u00d4\u0001\u0000\u0000\u0000$\u00e2\u0001\u0000"+
		"\u0000\u0000&\u00ea\u0001\u0000\u0000\u0000(\u00f7\u0001\u0000\u0000\u0000"+
		"*\u0102\u0001\u0000\u0000\u0000,\u010d\u0001\u0000\u0000\u0000.\u0116"+
		"\u0001\u0000\u0000\u00000\u0122\u0001\u0000\u0000\u00002\u012d\u0001\u0000"+
		"\u0000\u00004\u0135\u0001\u0000\u0000\u00006\u0140\u0001\u0000\u0000\u0000"+
		"8\u0150\u0001\u0000\u0000\u0000:\u0156\u0001\u0000\u0000\u0000<\u015f"+
		"\u0001\u0000\u0000\u0000>\u0168\u0001\u0000\u0000\u0000@\u016f\u0001\u0000"+
		"\u0000\u0000B\u017a\u0001\u0000\u0000\u0000D\u0183\u0001\u0000\u0000\u0000"+
		"F\u0190\u0001\u0000\u0000\u0000H\u0195\u0001\u0000\u0000\u0000J\u0197"+
		"\u0001\u0000\u0000\u0000L\u019b\u0001\u0000\u0000\u0000N\u019d\u0001\u0000"+
		"\u0000\u0000P\u01a4\u0001\u0000\u0000\u0000R\u01a6\u0001\u0000\u0000\u0000"+
		"T\u01a8\u0001\u0000\u0000\u0000V\u01b3\u0001\u0000\u0000\u0000X\u01b5"+
		"\u0001\u0000\u0000\u0000Z\u01b7\u0001\u0000\u0000\u0000\\\u01c0\u0001"+
		"\u0000\u0000\u0000^\u01c9\u0001\u0000\u0000\u0000`\u01cd\u0001\u0000\u0000"+
		"\u0000bn\u0003\u0004\u0002\u0000cn\u0003 \u0010\u0000dn\u0003\"\u0011"+
		"\u0000en\u0003(\u0014\u0000fn\u0003*\u0015\u0000gn\u00034\u001a\u0000"+
		"hn\u00036\u001b\u0000in\u0003:\u001d\u0000jn\u00030\u0018\u0000kn\u0003"+
		"2\u0019\u0000ln\u0003\u0016\u000b\u0000mb\u0001\u0000\u0000\u0000mc\u0001"+
		"\u0000\u0000\u0000md\u0001\u0000\u0000\u0000me\u0001\u0000\u0000\u0000"+
		"mf\u0001\u0000\u0000\u0000mg\u0001\u0000\u0000\u0000mh\u0001\u0000\u0000"+
		"\u0000mi\u0001\u0000\u0000\u0000mj\u0001\u0000\u0000\u0000mk\u0001\u0000"+
		"\u0000\u0000ml\u0001\u0000\u0000\u0000nq\u0001\u0000\u0000\u0000om\u0001"+
		"\u0000\u0000\u0000op\u0001\u0000\u0000\u0000p\u0001\u0001\u0000\u0000"+
		"\u0000qo\u0001\u0000\u0000\u0000rs\u0007\u0000\u0000\u0000s\u0003\u0001"+
		"\u0000\u0000\u0000tv\u0005\u0012\u0000\u0000uw\u0003\u0018\f\u0000vu\u0001"+
		"\u0000\u0000\u0000vw\u0001\u0000\u0000\u0000wx\u0001\u0000\u0000\u0000"+
		"xz\u0005\u0001\u0000\u0000y{\u0003\u0006\u0003\u0000zy\u0001\u0000\u0000"+
		"\u0000{|\u0001\u0000\u0000\u0000|z\u0001\u0000\u0000\u0000|}\u0001\u0000"+
		"\u0000\u0000}~\u0001\u0000\u0000\u0000~\u007f\u0005\u0002\u0000\u0000"+
		"\u007f\u0005\u0001\u0000\u0000\u0000\u0080\u0084\u0003\b\u0004\u0000\u0081"+
		"\u0084\u0003\n\u0005\u0000\u0082\u0084\u0003\f\u0006\u0000\u0083\u0080"+
		"\u0001\u0000\u0000\u0000\u0083\u0081\u0001\u0000\u0000\u0000\u0083\u0082"+
		"\u0001\u0000\u0000\u0000\u0084\u0007\u0001\u0000\u0000\u0000\u0085\u0086"+
		"\u0005\u001b\u0000\u0000\u0086\u0087\u0005\u0003\u0000\u0000\u0087\u0088"+
		"\u0003P(\u0000\u0088\t\u0001\u0000\u0000\u0000\u0089\u008a\u0005\u001c"+
		"\u0000\u0000\u008a\u008b\u0005\u0003\u0000\u0000\u008b\u008c\u0003P(\u0000"+
		"\u008c\u000b\u0001\u0000\u0000\u0000\u008d\u008e\u0005\u001d\u0000\u0000"+
		"\u008e\u008f\u0005\u0003\u0000\u0000\u008f\u0090\u0003P(\u0000\u0090\r"+
		"\u0001\u0000\u0000\u0000\u0091\u0092\u0003\u0010\b\u0000\u0092\u0093\u0005"+
		"\u0004\u0000\u0000\u0093\u0095\u0001\u0000\u0000\u0000\u0094\u0091\u0001"+
		"\u0000\u0000\u0000\u0095\u0098\u0001\u0000\u0000\u0000\u0096\u0094\u0001"+
		"\u0000\u0000\u0000\u0096\u0097\u0001\u0000\u0000\u0000\u0097\u0099\u0001"+
		"\u0000\u0000\u0000\u0098\u0096\u0001\u0000\u0000\u0000\u0099\u009a\u0003"+
		"\u0010\b\u0000\u009a\u000f\u0001\u0000\u0000\u0000\u009b\u009e\u0003\u0012"+
		"\t\u0000\u009c\u009e\u0003\u0014\n\u0000\u009d\u009b\u0001\u0000\u0000"+
		"\u0000\u009d\u009c\u0001\u0000\u0000\u0000\u009e\u0011\u0001\u0000\u0000"+
		"\u0000\u009f\u00a0\u0005\r\u0000\u0000\u00a0\u0013\u0001\u0000\u0000\u0000"+
		"\u00a1\u00a2\u0005\u000e\u0000\u0000\u00a2\u0015\u0001\u0000\u0000\u0000"+
		"\u00a3\u00a5\u0003\u0002\u0001\u0000\u00a4\u00a3\u0001\u0000\u0000\u0000"+
		"\u00a4\u00a5\u0001\u0000\u0000\u0000\u00a5\u00a6\u0001\u0000\u0000\u0000"+
		"\u00a6\u00a7\u0005\u0016\u0000\u0000\u00a7\u00a8\u0005\u0005\u0000\u0000"+
		"\u00a8\u00aa\u0003P(\u0000\u00a9\u00ab\u0003B!\u0000\u00aa\u00a9\u0001"+
		"\u0000\u0000\u0000\u00aa\u00ab\u0001\u0000\u0000\u0000\u00ab\u00ac\u0001"+
		"\u0000\u0000\u0000\u00ac\u00ad\u0005\u0019\u0000\u0000\u00ad\u00ae\u0003"+
		"\u000e\u0007\u0000\u00ae\u0017\u0001\u0000\u0000\u0000\u00af\u00b1\u0003"+
		"\u001a\r\u0000\u00b0\u00af\u0001\u0000\u0000\u0000\u00b1\u00b2\u0001\u0000"+
		"\u0000\u0000\u00b2\u00b0\u0001\u0000\u0000\u0000\u00b2\u00b3\u0001\u0000"+
		"\u0000\u0000\u00b3\u0019\u0001\u0000\u0000\u0000\u00b4\u00b5\u0005\u0005"+
		"\u0000\u0000\u00b5\u00b7\u0003P(\u0000\u00b6\u00b8\u0003\u001c\u000e\u0000"+
		"\u00b7\u00b6\u0001\u0000\u0000\u0000\u00b7\u00b8\u0001\u0000\u0000\u0000"+
		"\u00b8\u001b\u0001\u0000\u0000\u0000\u00b9\u00bb\u0005\u0006\u0000\u0000"+
		"\u00ba\u00bc\u0003\u001e\u000f\u0000\u00bb\u00ba\u0001\u0000\u0000\u0000"+
		"\u00bc\u00bd\u0001\u0000\u0000\u0000\u00bd\u00bb\u0001\u0000\u0000\u0000"+
		"\u00bd\u00be\u0001\u0000\u0000\u0000\u00be\u00bf\u0001\u0000\u0000\u0000"+
		"\u00bf\u00c0\u0005\u0007\u0000\u0000\u00c0\u001d\u0001\u0000\u0000\u0000"+
		"\u00c1\u00c2\u0003P(\u0000\u00c2\u00c3\u0005\u0003\u0000\u0000\u00c3\u00c4"+
		"\u0003V+\u0000\u00c4\u001f\u0001\u0000\u0000\u0000\u00c5\u00c7\u0003\u0002"+
		"\u0001\u0000\u00c6\u00c5\u0001\u0000\u0000\u0000\u00c6\u00c7\u0001\u0000"+
		"\u0000\u0000\u00c7\u00c8\u0001\u0000\u0000\u0000\u00c8\u00c9\u0005\u000f"+
		"\u0000\u0000\u00c9\u00cb\u0003P(\u0000\u00ca\u00cc\u0003&\u0013\u0000"+
		"\u00cb\u00ca\u0001\u0000\u0000\u0000\u00cb\u00cc\u0001\u0000\u0000\u0000"+
		"\u00cc\u00ce\u0001\u0000\u0000\u0000\u00cd\u00cf\u0003\u0018\f\u0000\u00ce"+
		"\u00cd\u0001\u0000\u0000\u0000\u00ce\u00cf\u0001\u0000\u0000\u0000\u00cf"+
		"\u00d1\u0001\u0000\u0000\u0000\u00d0\u00d2\u0003$\u0012\u0000\u00d1\u00d0"+
		"\u0001\u0000\u0000\u0000\u00d1\u00d2\u0001\u0000\u0000\u0000\u00d2!\u0001"+
		"\u0000\u0000\u0000\u00d3\u00d5\u0003\u0002\u0001\u0000\u00d4\u00d3\u0001"+
		"\u0000\u0000\u0000\u00d4\u00d5\u0001\u0000\u0000\u0000\u00d5\u00d6\u0001"+
		"\u0000\u0000\u0000\u00d6\u00d7\u0005\u0017\u0000\u0000\u00d7\u00d8\u0005"+
		"\u000f\u0000\u0000\u00d8\u00da\u0003P(\u0000\u00d9\u00db\u0003&\u0013"+
		"\u0000\u00da\u00d9\u0001\u0000\u0000\u0000\u00da\u00db\u0001\u0000\u0000"+
		"\u0000\u00db\u00dd\u0001\u0000\u0000\u0000\u00dc\u00de\u0003\u0018\f\u0000"+
		"\u00dd\u00dc\u0001\u0000\u0000\u0000\u00dd\u00de\u0001\u0000\u0000\u0000"+
		"\u00de\u00e0\u0001\u0000\u0000\u0000\u00df\u00e1\u0003$\u0012\u0000\u00e0"+
		"\u00df\u0001\u0000\u0000\u0000\u00e0\u00e1\u0001\u0000\u0000\u0000\u00e1"+
		"#\u0001\u0000\u0000\u0000\u00e2\u00e4\u0005\u0001\u0000\u0000\u00e3\u00e5"+
		"\u0003@ \u0000\u00e4\u00e3\u0001\u0000\u0000\u0000\u00e5\u00e6\u0001\u0000"+
		"\u0000\u0000\u00e6\u00e4\u0001\u0000\u0000\u0000\u00e6\u00e7\u0001\u0000"+
		"\u0000\u0000\u00e7\u00e8\u0001\u0000\u0000\u0000\u00e8\u00e9\u0005\u0002"+
		"\u0000\u0000\u00e9%\u0001\u0000\u0000\u0000\u00ea\u00ec\u0005\u0010\u0000"+
		"\u0000\u00eb\u00ed\u0005\b\u0000\u0000\u00ec\u00eb\u0001\u0000\u0000\u0000"+
		"\u00ec\u00ed\u0001\u0000\u0000\u0000\u00ed\u00ee\u0001\u0000\u0000\u0000"+
		"\u00ee\u00f3\u0005\"\u0000\u0000\u00ef\u00f0\u0005\b\u0000\u0000\u00f0"+
		"\u00f2\u0005\"\u0000\u0000\u00f1\u00ef\u0001\u0000\u0000\u0000\u00f2\u00f5"+
		"\u0001\u0000\u0000\u0000\u00f3\u00f1\u0001\u0000\u0000\u0000\u00f3\u00f4"+
		"\u0001\u0000\u0000\u0000\u00f4\'\u0001\u0000\u0000\u0000\u00f5\u00f3\u0001"+
		"\u0000\u0000\u0000\u00f6\u00f8\u0003\u0002\u0001\u0000\u00f7\u00f6\u0001"+
		"\u0000\u0000\u0000\u00f7\u00f8\u0001\u0000\u0000\u0000\u00f8\u00f9\u0001"+
		"\u0000\u0000\u0000\u00f9\u00fa\u0005\u0015\u0000\u0000\u00fa\u00fc\u0003"+
		"P(\u0000\u00fb\u00fd\u0003\u0018\f\u0000\u00fc\u00fb\u0001\u0000\u0000"+
		"\u0000\u00fc\u00fd\u0001\u0000\u0000\u0000\u00fd\u00ff\u0001\u0000\u0000"+
		"\u0000\u00fe\u0100\u0003,\u0016\u0000\u00ff\u00fe\u0001\u0000\u0000\u0000"+
		"\u00ff\u0100\u0001\u0000\u0000\u0000\u0100)\u0001\u0000\u0000\u0000\u0101"+
		"\u0103\u0003\u0002\u0001\u0000\u0102\u0101\u0001\u0000\u0000\u0000\u0102"+
		"\u0103\u0001\u0000\u0000\u0000\u0103\u0104\u0001\u0000\u0000\u0000\u0104"+
		"\u0105\u0005\u0017\u0000\u0000\u0105\u0106\u0005\u0015\u0000\u0000\u0106"+
		"\u0108\u0003P(\u0000\u0107\u0109\u0003\u0018\f\u0000\u0108\u0107\u0001"+
		"\u0000\u0000\u0000\u0108\u0109\u0001\u0000\u0000\u0000\u0109\u010b\u0001"+
		"\u0000\u0000\u0000\u010a\u010c\u0003,\u0016\u0000\u010b\u010a\u0001\u0000"+
		"\u0000\u0000\u010b\u010c\u0001\u0000\u0000\u0000\u010c+\u0001\u0000\u0000"+
		"\u0000\u010d\u010f\u0005\u0001\u0000\u0000\u010e\u0110\u0003.\u0017\u0000"+
		"\u010f\u010e\u0001\u0000\u0000\u0000\u0110\u0111\u0001\u0000\u0000\u0000"+
		"\u0111\u010f\u0001\u0000\u0000\u0000\u0111\u0112\u0001\u0000\u0000\u0000"+
		"\u0112\u0113\u0001\u0000\u0000\u0000\u0113\u0114\u0005\u0002\u0000\u0000"+
		"\u0114-\u0001\u0000\u0000\u0000\u0115\u0117\u0003\u0002\u0001\u0000\u0116"+
		"\u0115\u0001\u0000\u0000\u0000\u0116\u0117\u0001\u0000\u0000\u0000\u0117"+
		"\u0118\u0001\u0000\u0000\u0000\u0118\u0119\u0003P(\u0000\u0119\u011a\u0005"+
		"\u0003\u0000\u0000\u011a\u011c\u0003F#\u0000\u011b\u011d\u0003N\'\u0000"+
		"\u011c\u011b\u0001\u0000\u0000\u0000\u011c\u011d\u0001\u0000\u0000\u0000"+
		"\u011d\u011f\u0001\u0000\u0000\u0000\u011e\u0120\u0003\u0018\f\u0000\u011f"+
		"\u011e\u0001\u0000\u0000\u0000\u011f\u0120\u0001\u0000\u0000\u0000\u0120"+
		"/\u0001\u0000\u0000\u0000\u0121\u0123\u0003\u0002\u0001\u0000\u0122\u0121"+
		"\u0001\u0000\u0000\u0000\u0122\u0123\u0001\u0000\u0000\u0000\u0123\u0124"+
		"\u0001\u0000\u0000\u0000\u0124\u0125\u0005\u0011\u0000\u0000\u0125\u0127"+
		"\u0003P(\u0000\u0126\u0128\u0003\u0018\f\u0000\u0127\u0126\u0001\u0000"+
		"\u0000\u0000\u0127\u0128\u0001\u0000\u0000\u0000\u0128\u012a\u0001\u0000"+
		"\u0000\u0000\u0129\u012b\u0003$\u0012\u0000\u012a\u0129\u0001\u0000\u0000"+
		"\u0000\u012a\u012b\u0001\u0000\u0000\u0000\u012b1\u0001\u0000\u0000\u0000"+
		"\u012c\u012e\u0003\u0002\u0001\u0000\u012d\u012c\u0001\u0000\u0000\u0000"+
		"\u012d\u012e\u0001\u0000\u0000\u0000\u012e\u012f\u0001\u0000\u0000\u0000"+
		"\u012f\u0130\u0005\u0018\u0000\u0000\u0130\u0132\u0003P(\u0000\u0131\u0133"+
		"\u0003\u0018\f\u0000\u0132\u0131\u0001\u0000\u0000\u0000\u0132\u0133\u0001"+
		"\u0000\u0000\u0000\u01333\u0001\u0000\u0000\u0000\u0134\u0136\u0003\u0002"+
		"\u0001\u0000\u0135\u0134\u0001\u0000\u0000\u0000\u0135\u0136\u0001\u0000"+
		"\u0000\u0000\u0136\u0137\u0001\u0000\u0000\u0000\u0137\u0138\u0005\u0014"+
		"\u0000\u0000\u0138\u013a\u0003P(\u0000\u0139\u013b\u0003\u0018\f\u0000"+
		"\u013a\u0139\u0001\u0000\u0000\u0000\u013a\u013b\u0001\u0000\u0000\u0000"+
		"\u013b\u013c\u0001\u0000\u0000\u0000\u013c\u013d\u0005\t\u0000\u0000\u013d"+
		"\u013e\u00038\u001c\u0000\u013e5\u0001\u0000\u0000\u0000\u013f\u0141\u0003"+
		"\u0002\u0001\u0000\u0140\u013f\u0001\u0000\u0000\u0000\u0140\u0141\u0001"+
		"\u0000\u0000\u0000\u0141\u0142\u0001\u0000\u0000\u0000\u0142\u0143\u0005"+
		"\u0017\u0000\u0000\u0143\u0144\u0005\u0014\u0000\u0000\u0144\u0146\u0003"+
		"P(\u0000\u0145\u0147\u0003\u0018\f\u0000\u0146\u0145\u0001\u0000\u0000"+
		"\u0000\u0146\u0147\u0001\u0000\u0000\u0000\u0147\u0148\u0001\u0000\u0000"+
		"\u0000\u0148\u0149\u0005\t\u0000\u0000\u0149\u014a\u00038\u001c\u0000"+
		"\u014a7\u0001\u0000\u0000\u0000\u014b\u014c\u0003P(\u0000\u014c\u014d"+
		"\u0005\u0004\u0000\u0000\u014d\u014f\u0001\u0000\u0000\u0000\u014e\u014b"+
		"\u0001\u0000\u0000\u0000\u014f\u0152\u0001\u0000\u0000\u0000\u0150\u014e"+
		"\u0001\u0000\u0000\u0000\u0150\u0151\u0001\u0000\u0000\u0000\u0151\u0153"+
		"\u0001\u0000\u0000\u0000\u0152\u0150\u0001\u0000\u0000\u0000\u0153\u0154"+
		"\u0003P(\u0000\u01549\u0001\u0000\u0000\u0000\u0155\u0157\u0003\u0002"+
		"\u0001\u0000\u0156\u0155\u0001\u0000\u0000\u0000\u0156\u0157\u0001\u0000"+
		"\u0000\u0000\u0157\u0158\u0001\u0000\u0000\u0000\u0158\u0159\u0005\u0013"+
		"\u0000\u0000\u0159\u015b\u0003P(\u0000\u015a\u015c\u0003\u0018\f\u0000"+
		"\u015b\u015a\u0001\u0000\u0000\u0000\u015b\u015c\u0001\u0000\u0000\u0000"+
		"\u015c\u015d\u0001\u0000\u0000\u0000\u015d\u015e\u0003<\u001e\u0000\u015e"+
		";\u0001\u0000\u0000\u0000\u015f\u0161\u0005\u0001\u0000\u0000\u0160\u0162"+
		"\u0003>\u001f\u0000\u0161\u0160\u0001\u0000\u0000\u0000\u0162\u0163\u0001"+
		"\u0000\u0000\u0000\u0163\u0161\u0001\u0000\u0000\u0000\u0163\u0164\u0001"+
		"\u0000\u0000\u0000\u0164\u0165\u0001\u0000\u0000\u0000\u0165\u0166\u0005"+
		"\u0002\u0000\u0000\u0166=\u0001\u0000\u0000\u0000\u0167\u0169\u0003\u0002"+
		"\u0001\u0000\u0168\u0167\u0001\u0000\u0000\u0000\u0168\u0169\u0001\u0000"+
		"\u0000\u0000\u0169\u016a\u0001\u0000\u0000\u0000\u016a\u016c\u0003R)\u0000"+
		"\u016b\u016d\u0003\u0018\f\u0000\u016c\u016b\u0001\u0000\u0000\u0000\u016c"+
		"\u016d\u0001\u0000\u0000\u0000\u016d?\u0001\u0000\u0000\u0000\u016e\u0170"+
		"\u0003\u0002\u0001\u0000\u016f\u016e\u0001\u0000\u0000\u0000\u016f\u0170"+
		"\u0001\u0000\u0000\u0000\u0170\u0171\u0001\u0000\u0000\u0000\u0171\u0173"+
		"\u0003P(\u0000\u0172\u0174\u0003B!\u0000\u0173\u0172\u0001\u0000\u0000"+
		"\u0000\u0173\u0174\u0001\u0000\u0000\u0000\u0174\u0175\u0001\u0000\u0000"+
		"\u0000\u0175\u0176\u0005\u0003\u0000\u0000\u0176\u0178\u0003F#\u0000\u0177"+
		"\u0179\u0003\u0018\f\u0000\u0178\u0177\u0001\u0000\u0000\u0000\u0178\u0179"+
		"\u0001\u0000\u0000\u0000\u0179A\u0001\u0000\u0000\u0000\u017a\u017c\u0005"+
		"\u0006\u0000\u0000\u017b\u017d\u0003D\"\u0000\u017c\u017b\u0001\u0000"+
		"\u0000\u0000\u017d\u017e\u0001\u0000\u0000\u0000\u017e\u017c\u0001\u0000"+
		"\u0000\u0000\u017e\u017f\u0001\u0000\u0000\u0000\u017f\u0180\u0001\u0000"+
		"\u0000\u0000\u0180\u0181\u0005\u0007\u0000\u0000\u0181C\u0001\u0000\u0000"+
		"\u0000\u0182\u0184\u0003\u0002\u0001\u0000\u0183\u0182\u0001\u0000\u0000"+
		"\u0000\u0183\u0184\u0001\u0000\u0000\u0000\u0184\u0185\u0001\u0000\u0000"+
		"\u0000\u0185\u0186\u0003P(\u0000\u0186\u0187\u0005\u0003\u0000\u0000\u0187"+
		"\u0189\u0003F#\u0000\u0188\u018a\u0003N\'\u0000\u0189\u0188\u0001\u0000"+
		"\u0000\u0000\u0189\u018a\u0001\u0000\u0000\u0000\u018a\u018c\u0001\u0000"+
		"\u0000\u0000\u018b\u018d\u0003\u0018\f\u0000\u018c\u018b\u0001\u0000\u0000"+
		"\u0000\u018c\u018d\u0001\u0000\u0000\u0000\u018dE\u0001\u0000\u0000\u0000"+
		"\u018e\u0191\u0003H$\u0000\u018f\u0191\u0003J%\u0000\u0190\u018e\u0001"+
		"\u0000\u0000\u0000\u0190\u018f\u0001\u0000\u0000\u0000\u0191\u0193\u0001"+
		"\u0000\u0000\u0000\u0192\u0194\u0003L&\u0000\u0193\u0192\u0001\u0000\u0000"+
		"\u0000\u0193\u0194\u0001\u0000\u0000\u0000\u0194G\u0001\u0000\u0000\u0000"+
		"\u0195\u0196\u0003P(\u0000\u0196I\u0001\u0000\u0000\u0000\u0197\u0198"+
		"\u0005\n\u0000\u0000\u0198\u0199\u0003F#\u0000\u0199\u019a\u0005\u000b"+
		"\u0000\u0000\u019aK\u0001\u0000\u0000\u0000\u019b\u019c\u0005\f\u0000"+
		"\u0000\u019cM\u0001\u0000\u0000\u0000\u019d\u019e\u0005\t\u0000\u0000"+
		"\u019e\u019f\u0003V+\u0000\u019fO\u0001\u0000\u0000\u0000\u01a0\u01a5"+
		"\u0003R)\u0000\u01a1\u01a5\u0005\u001f\u0000\u0000\u01a2\u01a5\u0005 "+
		"\u0000\u0000\u01a3\u01a5\u0005!\u0000\u0000\u01a4\u01a0\u0001\u0000\u0000"+
		"\u0000\u01a4\u01a1\u0001\u0000\u0000\u0000\u01a4\u01a2\u0001\u0000\u0000"+
		"\u0000\u01a4\u01a3\u0001\u0000\u0000\u0000\u01a5Q\u0001\u0000\u0000\u0000"+
		"\u01a6\u01a7\u0007\u0001\u0000\u0000\u01a7S\u0001\u0000\u0000\u0000\u01a8"+
		"\u01a9\u0007\u0002\u0000\u0000\u01a9U\u0001\u0000\u0000\u0000\u01aa\u01b4"+
		"\u0005#\u0000\u0000\u01ab\u01b4\u0005$\u0000\u0000\u01ac\u01b4\u0005*"+
		"\u0000\u0000\u01ad\u01b4\u0005+\u0000\u0000\u01ae\u01b4\u0003T*\u0000"+
		"\u01af\u01b4\u0003`0\u0000\u01b0\u01b4\u0003X,\u0000\u01b1\u01b4\u0003"+
		"Z-\u0000\u01b2\u01b4\u0003\\.\u0000\u01b3\u01aa\u0001\u0000\u0000\u0000"+
		"\u01b3\u01ab\u0001\u0000\u0000\u0000\u01b3\u01ac\u0001\u0000\u0000\u0000"+
		"\u01b3\u01ad\u0001\u0000\u0000\u0000\u01b3\u01ae\u0001\u0000\u0000\u0000"+
		"\u01b3\u01af\u0001\u0000\u0000\u0000\u01b3\u01b0\u0001\u0000\u0000\u0000"+
		"\u01b3\u01b1\u0001\u0000\u0000\u0000\u01b3\u01b2\u0001\u0000\u0000\u0000"+
		"\u01b4W\u0001\u0000\u0000\u0000\u01b5\u01b6\u0003R)\u0000\u01b6Y\u0001"+
		"\u0000\u0000\u0000\u01b7\u01bb\u0005\n\u0000\u0000\u01b8\u01ba\u0003V"+
		"+\u0000\u01b9\u01b8\u0001\u0000\u0000\u0000\u01ba\u01bd\u0001\u0000\u0000"+
		"\u0000\u01bb\u01b9\u0001\u0000\u0000\u0000\u01bb\u01bc\u0001\u0000\u0000"+
		"\u0000\u01bc\u01be\u0001\u0000\u0000\u0000\u01bd\u01bb\u0001\u0000\u0000"+
		"\u0000\u01be\u01bf\u0005\u000b\u0000\u0000\u01bf[\u0001\u0000\u0000\u0000"+
		"\u01c0\u01c4\u0005\u0001\u0000\u0000\u01c1\u01c3\u0003^/\u0000\u01c2\u01c1"+
		"\u0001\u0000\u0000\u0000\u01c3\u01c6\u0001\u0000\u0000\u0000\u01c4\u01c2"+
		"\u0001\u0000\u0000\u0000\u01c4\u01c5\u0001\u0000\u0000\u0000\u01c5\u01c7"+
		"\u0001\u0000\u0000\u0000\u01c6\u01c4\u0001\u0000\u0000\u0000\u01c7\u01c8"+
		"\u0005\u0002\u0000\u0000\u01c8]\u0001\u0000\u0000\u0000\u01c9\u01ca\u0003"+
		"P(\u0000\u01ca\u01cb\u0005\u0003\u0000\u0000\u01cb\u01cc\u0003V+\u0000"+
		"\u01cc_\u0001\u0000\u0000\u0000\u01cd\u01ce\u0005!\u0000\u0000\u01cea"+
		"\u0001\u0000\u0000\u0000=mov|\u0083\u0096\u009d\u00a4\u00aa\u00b2\u00b7"+
		"\u00bd\u00c6\u00cb\u00ce\u00d1\u00d4\u00da\u00dd\u00e0\u00e6\u00ec\u00f3"+
		"\u00f7\u00fc\u00ff\u0102\u0108\u010b\u0111\u0116\u011c\u011f\u0122\u0127"+
		"\u012a\u012d\u0132\u0135\u013a\u0140\u0146\u0150\u0156\u015b\u0163\u0168"+
		"\u016c\u016f\u0173\u0178\u017e\u0183\u0189\u018c\u0190\u0193\u01a4\u01b3"+
		"\u01bb\u01c4";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}