grammar Natural;

options { caseInsensitive = true; }

program
    : defineData? statement* END_KW? EOF
    ;

defineData
    : DEFINE DATA LOCAL variableDecl* END_DEFINE
    ;

variableDecl
    : level=INT name=VAR LPAREN typeSpec RPAREN
    ;

typeSpec
    : base=IDENT ((DOT | COMMA) frac=INT)?
    ;

statement
    : moveStmt
    | computeStmt
    | assignStmt
    | ifStmt
    | forStmt
    | repeatStmt
    | writeStmt
    | performStmt
    | subroutineDef
    ;

moveStmt      : MOVE expr TO varRef ;
computeStmt   : COMPUTE varRef EQ_SYM expr ;
assignStmt    : varRef (ASSIGN_SYM | EQ_SYM) expr ;
ifStmt        : IF condition statement* (ELSE statement*)? END_IF ;
forStmt       : FOR varRef (EQ_SYM | ASSIGN_SYM) from=expr TO to=expr (STEP step=expr)? statement* END_FOR ;
repeatStmt    : REPEAT statement* UNTIL condition END_REPEAT ;
performStmt   : PERFORM IDENT ;
subroutineDef : DEFINE SUBROUTINE IDENT statement* END_SUBROUTINE ;

writeStmt
    : WRITE operand+
    ;

operand
    : literal
    | varRef
    ;

condition
    : condition AND condition          # AndCond
    | condition OR condition           # OrCond
    | NOT condition                    # NotCond
    | LPAREN condition RPAREN          # ParenCond
    | comparison                       # CompareCond
    ;

comparison
    : expr relop expr
    ;

relop
    : EQ_SYM | EQ_KW
    | NE_SYM | NE_KW
    | GE_SYM | GE_KW
    | LE_SYM | LE_KW
    | GT_SYM | GT_KW
    | LT_SYM | LT_KW
    ;

expr
    : expr op=(STAR | SLASH) expr      # MulDiv
    | expr op=(PLUS | MINUS) expr      # AddSub
    | MINUS expr                       # NegExpr
    | primary                          # PrimaryExpr
    ;

primary
    : literal                          # LiteralPrimary
    | varRef                           # VarPrimary
    | LPAREN expr RPAREN               # ParenPrimary
    ;

varRef  : VAR ;

literal
    : STRING                           # StringLiteral
    | NUMBER                           # DecimalLiteral
    | INT                              # IntLiteral
    | TRUE_KW                          # TrueLiteral
    | FALSE_KW                         # FalseLiteral
    ;

// a '*' only starts a comment in column 1, everywhere else it is multiply
LINE_COMMENT   : {getCharPositionInLine() == 0}? '*' ~[\r\n]* -> skip ;
INLINE_COMMENT : '/*' ~[\r\n]*                                -> skip ;

DEFINE          : 'DEFINE' ;
DATA            : 'DATA' ;
LOCAL           : 'LOCAL' ;
END_DEFINE      : 'END-DEFINE' ;
SUBROUTINE      : 'SUBROUTINE' ;
END_SUBROUTINE  : 'END-SUBROUTINE' ;
MOVE            : 'MOVE' ;
COMPUTE         : 'COMPUTE' ;
TO              : 'TO' ;
IF              : 'IF' ;
ELSE            : 'ELSE' ;
END_IF          : 'END-IF' ;
FOR             : 'FOR' ;
STEP            : 'STEP' ;
END_FOR         : 'END-FOR' ;
REPEAT          : 'REPEAT' ;
UNTIL           : 'UNTIL' ;
END_REPEAT      : 'END-REPEAT' ;
WRITE           : 'WRITE' ;
PERFORM         : 'PERFORM' ;
END_KW          : 'END' ;

AND             : 'AND' ;
OR              : 'OR' ;
NOT             : 'NOT' ;
TRUE_KW         : 'TRUE' ;
FALSE_KW        : 'FALSE' ;

EQ_KW           : 'EQ' ;
NE_KW           : 'NE' ;
GT_KW           : 'GT' ;
LT_KW           : 'LT' ;
GE_KW           : 'GE' ;
LE_KW           : 'LE' ;

ASSIGN_SYM      : ':=' ;
NE_SYM          : '<>' | '!=' ;
GE_SYM          : '>=' ;
LE_SYM          : '<=' ;
EQ_SYM          : '=' ;
GT_SYM          : '>' ;
LT_SYM          : '<' ;

PLUS            : '+' ;
MINUS           : '-' ;
STAR            : '*' ;
SLASH           : '/' ;
LPAREN          : '(' ;
RPAREN          : ')' ;
DOT             : '.' ;
COMMA           : ',' ;

VAR     : '#' [A-Z0-9#_] ([A-Z0-9#_-]* [A-Z0-9#_])? ;
NUMBER  : [0-9]+ '.' [0-9]+ ;
INT     : [0-9]+ ;
STRING  : '\'' ( '\'\'' | ~['\r\n] )* '\'' ;
IDENT   : [A-Z] [A-Z0-9_-]* ;

WS      : [ \t\r\n]+ -> skip ;
