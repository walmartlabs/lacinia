grammar GraphqlSchema;

graphqlSchema
  : (schemaDef|typeDef|inputTypeDef|unionDef|enumDef|interfaceDef|scalarDef|directiveDef)*
  ;

description
  : StringValue
  | BlockStringValue
  ;

schemaDef
  : 'schema' directiveList? '{' operationTypeDef+ '}'
  ;

operationTypeDef
  : queryOperationDef
  | mutationOperationDef
  | subscriptionOperationDef
  ;

queryOperationDef
  : 'query' ':' Name
  ;

mutationOperationDef
  : 'mutation' ':' Name
  ;

subscriptionOperationDef
  : 'subscription' ':' Name
  ;

directiveLocationList
  : (directiveLocation '|')* directiveLocation
  ;

directiveLocation
  : executableDirectiveLocation
  | typeSystemDirectiveLocation
  ;

executableDirectiveLocation
  : 'QUERY' | 'MUTATION' | 'SUBSCRIPTION' | 'FIELD' | 'FRAGMENT_DEFINITION' | 'FRAGMENT_SPREAD'
  | 'INLINE_FRAGMENT'
  ;

typeSystemDirectiveLocation
  : 'SCHEMA' | 'SCALAR' | 'OBJECT' | 'FIELD_DEFINITION' | 'ARGUMENT_DEFINITION'
  | 'INTERFACE' | 'UNION' | 'ENUM' | 'ENUM_VALUE' | 'INPUT_OBJECT' | 'INPUT_FIELD_DEFINITION'
  ;

directiveDef
  : description? 'directive' '@' Name argList? 'on' directiveLocationList
  ;


directiveList
  : directive+
  ;

directive
  : '@' Name directiveArgList?
  ;

directiveArgList
  : '(' directiveArg+ ')'
  ;

directiveArg
  : Name ':' value
  ;

typeDef
  : description? 'type' Name implementationDef? directiveList? fieldDefs
  ;

fieldDefs
  : '{' fieldDef+ '}'
  ;

implementationDef
  : 'implements' Name+
  ;

inputTypeDef
  : description? 'input' Name directiveList? fieldDefs
  ;

interfaceDef
  : description? 'interface' Name directiveList? fieldDefs
  ;

scalarDef
  : description? 'scalar' Name directiveList?
  ;

unionDef
  : description? 'union' Name directiveList? '=' unionTypes
  ;

unionTypes
  : (Name '|')* Name
  ;

enumDef
  : description? 'enum' Name directiveList? enumValueDefs
  ;

enumValueDefs
  : '{' enumValueDef+ '}'
  ;

enumValueDef
  : description? Name directiveList?
  ;

fieldDef
  : description? Name argList? ':' typeSpec directiveList?
  ;

argList
  : '(' argument+ ')'
  ;

argument
  : description? Name ':' typeSpec defaultValue? directiveList?
  ;

typeSpec
  : (typeName|listType) required?
  ;


/* This is a hook that allows the parser to follow the convention that
   references to default scalar types use a Symbol, not a Keyword. */

typeName
  : Name;

listType
  : '[' typeSpec ']'
  ;

required
  : '!'
  ;

defaultValue
  : '=' value
  ;

BooleanValue
    : 'true'
    | 'false'
    ;

Name
  : [_A-Za-z][_0-9A-Za-z]*
  ;

value
    : IntValue
    | FloatValue
    | StringValue
    | BlockStringValue
    | BooleanValue
    | NullValue
    | enumValue
    | arrayValue
    | objectValue
    ;

enumValue
    : Name
    ;

arrayValue
    : '[' value* ']'
    ;

objectValue
    : '{' objectField* '}'
    ;

objectField
    : Name ':' value
    ;

NullValue
    : Null
    ;

Null
    : 'null'
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
