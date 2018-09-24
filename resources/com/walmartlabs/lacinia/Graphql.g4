grammar Graphql;

document
    : definition+
    ;

definition
    : operationDefinition
    | fragmentDefinition
    ;

operationDefinition
    : selectionSet
    | operationType name? variableDefinitions? directives? selectionSet
    ;

name
  : 'query'
  | 'mutation'
  | 'subscription'
  | NameId
  ;

operationType
    : 'query'
    | 'mutation'
    | 'subscription'
    ;


variableDefinitions
    : '(' variableDefinition+ ')'
    ;

variableDefinition
    : variable ':' type defaultValue?
    ;

variable
    : '$' name
    ;

defaultValue
    : '=' value
    ;

selectionSet
    : '{' selection+ '}'
    ;

selection
    : field
    | fragmentSpread
    | inlineFragment
    ;

field
    : alias? name arguments? directives? selectionSet?
    ;

alias
    : name ':'
    ;

arguments
    : '(' argument+ ')'
    ;

argument
    : name ':' value
    ;

fragmentSpread
    : '...' fragmentName directives?
    ;

inlineFragment
    : '...' 'on' typeCondition directives? selectionSet
    ;

fragmentDefinition
    : 'fragment' fragmentName 'on' typeCondition directives? selectionSet
    ;

fragmentName
    : name
    ;

typeCondition
    : typeName
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
    | variable
    ;

enumValue
    : name
    ;

arrayValue
    : '[' value* ']'
    ;

objectValue
    : '{' objectField* '}'
    ;

objectField
    : name ':' value
    ;

directives
    : directive+
    ;

directive
    : '@' name arguments?
    ;

type
    : typeName
    | listType
    | nonNullType
    ;

typeName
    : name
    ;

listType
    : '[' type ']'
    ;

nonNullType
    : typeName '!'
    | listType '!'
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

NameId
    : [_A-Za-z][_0-9A-Za-z]*
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
