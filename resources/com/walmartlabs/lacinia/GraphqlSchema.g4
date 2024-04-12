grammar GraphqlSchema;

graphqlSchema
  : (schemaDef|typeDef|typeExtDef|inputTypeDef|inputTypeExtDef|unionDef|unionExtDef|enumDef|interfaceDef|scalarDef|directiveDef)*
  ;

description
  : StringValue
  | BlockStringValue
  ;

schemaDef
  : K_SCHEMA directiveList? '{' operationTypeDef+ '}'
  ;

operationTypeDef
  : queryOperationDef
  | mutationOperationDef
  | subscriptionOperationDef
  ;

queryOperationDef
  : K_QUERY ':' anyName
  ;

mutationOperationDef
  : K_MUTATION ':' anyName
  ;

subscriptionOperationDef
  : K_SUBSCRIPTION ':' anyName
  ;

directiveLocationList
  : (directiveLocation '|')* directiveLocation
  ;

directiveLocation
  : executableDirectiveLocation
  | typeSystemDirectiveLocation
  ;

executableDirectiveLocation
  : EXECUTABLE_DIRECTIVE_LOCATION
  ;

typeSystemDirectiveLocation
  : TYPE_SYSTEM_DIRECTIVE_LOCATION
  ;

directiveDef
  : description? K_DIRECTIVE '@' anyName argList? K_ON directiveLocationList
  ;


directiveList
  : directive+
  ;

directive
  : '@' anyName directiveArgList?
  ;

directiveArgList
  : '(' directiveArg+ ')'
  ;

directiveArg
  : anyName ':' value
  ;

typeDef
  : description? K_TYPE anyName implementationDef? directiveList? fieldDefs?
  ;

typeExtDef
  : description? K_EXTEND K_TYPE anyName implementationDef? directiveList? fieldDefs?
  ;

fieldDefs
  : '{' fieldDef+ '}'
  ;

implementationDef
  : K_IMPLEMENTS '&'? Name ('&' Name)*
  ;

inputTypeDef
  : description? K_INPUT anyName directiveList? inputValueDefs?
  ;

inputTypeExtDef
  : description? K_EXTEND K_INPUT anyName directiveList? inputValueDefs?
  ;

inputValueDefs
  : '{' inputValueDef+ '}'
  ;

inputValueDef
  : description? anyName ':' typeSpec defaultValue? directiveList?
  ;

interfaceDef
  : description? K_INTERFACE anyName directiveList? fieldDefs?
  ;

scalarDef
  : description? K_SCALAR anyName directiveList?
  ;

unionDef
  : description? K_UNION anyName directiveList? '=' unionTypes
  ;

unionExtDef
  : description? K_EXTEND K_UNION anyName directiveList? '=' unionTypes
  ;

unionTypes
  : (anyName '|')* anyName
  ;

enumDef
  : description? K_ENUM anyName directiveList? enumValueDefs
  ;

enumValueDefs
  : '{' enumValueDef+ '}'
  ;

enumValueDef
  : description? nameTokens directiveList?
  ;

fieldDef
  : description? anyName argList? ':' typeSpec directiveList?
  ;

argList
  : '(' argument+ ')'
  ;

argument
  : description? anyName ':' typeSpec defaultValue? directiveList?
  ;

typeSpec
  : (typeName|listType) required?
  ;


/* This is a hook that allows the parser to follow the convention that
   references to default scalar types use a Symbol, not a Keyword. */

typeName
  : anyName;

listType
  : '[' typeSpec ']'
  ;

required
  : '!'
  ;

defaultValue
  : '=' value
  ;

anyName
  : nameTokens
  | K_TRUE
  | K_FALSE
  | K_NULL
  ;

nameTokens
  : Name
  | EXECUTABLE_DIRECTIVE_LOCATION
  | TYPE_SYSTEM_DIRECTIVE_LOCATION
  | K_TYPE
  | K_IMPLEMENTS
  | K_INTERFACE
  | K_SCHEMA
  | K_ENUM
  | K_UNION
  | K_INPUT
  | K_DIRECTIVE
  | K_EXTEND
  | K_SCALAR
  | K_ON
  | K_FRAGMENT
  | K_QUERY
  | K_MUTATION
  | K_SUBSCRIPTION
  | K_VALUE
  ;

EXECUTABLE_DIRECTIVE_LOCATION
  : 'QUERY' | 'MUTATION' | 'SUBSCRIPTION' | 'FIELD' | 'FRAGMENT_DEFINITION' | 'FRAGMENT_SPREAD'
  | 'INLINE_FRAGMENT'
  ;

TYPE_SYSTEM_DIRECTIVE_LOCATION
  : 'SCHEMA' | 'SCALAR' | 'OBJECT' | 'FIELD_DEFINITION' | 'ARGUMENT_DEFINITION'
  | 'INTERFACE' | 'UNION' | 'ENUM' | 'ENUM_VALUE' | 'INPUT_OBJECT' | 'INPUT_FIELD_DEFINITION'
  ;


K_TYPE         : 'type'         ;
K_IMPLEMENTS   : 'implements'   ;
K_INTERFACE    : 'interface'    ;
K_SCHEMA       : 'schema'       ;
K_ENUM         : 'enum'         ;
K_UNION        : 'union'        ;
K_INPUT        : 'input'        ;
K_DIRECTIVE    : 'directive'    ;
K_EXTEND       : 'extend'       ;
K_SCALAR       : 'scalar'       ;
K_ON           : 'on'           ;
K_FRAGMENT     : 'fragment'     ;
K_QUERY        : 'query'        ;
K_MUTATION     : 'mutation'     ;
K_SUBSCRIPTION : 'subscription' ;
K_VALUE        : 'value'        ;
K_TRUE         : 'true'         ;
K_FALSE        : 'false'        ;
K_NULL         : 'null'         ;

booleanValue
    : K_TRUE
    | K_FALSE
    ;

Name
  : [_A-Za-z][_0-9A-Za-z]*
  ;

value
    : IntValue
    | FloatValue
    | StringValue
    | BlockStringValue
    | booleanValue
    | nullValue
    | enumValue
    | arrayValue
    | objectValue
    ;

enumValue
    : nameTokens
    ;

arrayValue
    : '[' value* ']'
    ;

objectValue
    : '{' objectField* '}'
    ;

objectField
    : anyName ':' value
    ;

nullValue
    : K_NULL
    ;

IntValue
    : Sign? IntegerPart
    ;

FloatValue
    : Sign? IntegerPart ('.' Digit+)? ExponentPart?
    ;

Sign
    : '-'
    ;

IntegerPart
    : '0'
    | NonZeroDigit
    | NonZeroDigit Digit+
    ;

NonZeroDigit
    : '1'.. '9'
    ;

ExponentPart
    : ('e'|'E') Sign? Digit+
    ;

Digit
    : '0'..'9'
    ;

StringValue
    : DoubleQuote (~(["\\\n\r\u2028\u2029])|EscapedChar)* DoubleQuote
    ;

BlockStringValue
    : TripleQuote (.)*? TripleQuote
    ;

fragment EscapedChar
    :  '\\' (["\\/bfnrt] | Unicode)
    ;

fragment Unicode
   : 'u' Hex Hex Hex Hex
   ;

fragment DoubleQuote
   : '"'
   ;

fragment TripleQuote
  : '"""'
  ;

fragment Hex
   : [0-9a-fA-F]
   ;

Ignored
   : (Whitespace|Comma|LineTerminator|Comment) -> skip
   ;

fragment Comment
   : '#' ~[\n\r\u2028\u2029]*
   ;

fragment LineTerminator
   : [\n\r\u2028\u2029]
   ;

fragment Whitespace
   : [\t\u000b\f\u0020\u00a0]
   ;

fragment Comma
   : ','
   ;
