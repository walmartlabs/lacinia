grammar GraphqlSchema;

graphqlSchema
  : '{' (schemaDef|typeDef|inputTypeDef|unionDef|enumDef|interfaceDef|scalarDef)* '}'
  ;

schemaDef
  : 'schema' '{' operationTypeDef+ '}'
  ;

operationTypeDef
  : queryOperationDef
  | mutationOperationDef
  ;

queryOperationDef
  : 'query' ':' typeName
  ;

mutationOperationDef
  : 'mutation' ':' typeName
  ;

typeDef
  : 'type' typeName implementationDef? '{' fieldDef+ '}'
  ;

implementationDef
  : 'implements' typeName
  ;

inputTypeDef
  : 'input' typeName '{' fieldDef+ '}'
  ;

interfaceDef
  : 'interface' typeName '{' fieldDef+ '}'
  ;

scalarDef
  : 'scalar' typeName
  ;

unionDef
  : 'union' typeName '=' unionTypes
  ;

unionTypes
  : (typeName '|')* typeName
  ;

enumDef
  : 'enum' typeName '{' scalarName+ '}'
  ;

scalarName
  : Name
  ;

fieldDef
  : fieldName fieldArgs? ':' typeSpec
  ;

fieldArgs
  : '(' argument+ ')'
  ;

fieldName
  : Name
  ;

argument
  : Name ':' typeSpec defaultValue?
  ;

typeSpec
  : (typeName|listType) required?
  ;

listType
  : '[' typeSpec ']'
  ;

required
  : '!'
  ;

typeName
  : Name
  ;

defaultValue
  : '=' value
  ;

Name
  : [_A-Za-z][_0-9A-Za-z]*
  ;

value
    : IntValue
    | FloatValue
    | StringValue
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

BooleanValue
    : 'true'
    | 'false'
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

fragment EscapedChar
    :  '\\' (["\\/bfnrt] | Unicode)
    ;

fragment Unicode
   : 'u' Hex Hex Hex Hex
   ;

fragment DoubleQuote
   : '"'
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
