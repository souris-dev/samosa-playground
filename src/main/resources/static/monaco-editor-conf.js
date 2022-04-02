// require is provided by loader.min.js.
require.config({ paths: { 'vs': 'https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.33.0/min/vs' }});

require(["vs/editor/editor.main"], () => {
    // Register a new language
    monaco.languages.register({ id: 'samosa' });

    // Register a tokens provider for the language
    monaco.languages.setMonarchTokensProvider('samosa', {
      keywords: [
        'samosa', 'let', 'if', 'else', 'bro', 'true', 'yes', 'TRUE', 'True', 'false',
        'nope', 'FALSE', 'False', 'while', 'thanku_next', 'yamete_kudasai'
      ],

      typeKeywords: [
        'int', 'string', 'boolie'
      ],

      operators: [
        '=', '>', '<', '!', '==', '<=', '>=', '!=',
        '&&', '||', '+', '-', '*', '/', '||!', '!!', 'and', 'or', 'strictor', 'not',
        '...'
      ],

      // we include these common regular expressions
      symbols:  /[=><!~?:&|+\-*\/\^%]+/,

      // C# style strings
      escapes: /\\(?:[abfnrtv\\"']|x[0-9A-Fa-f]{1,4}|u[0-9A-Fa-f]{4}|U[0-9A-Fa-f]{8})/,

      // The main tokenizer for our languages
      tokenizer: {
        root: [
          // identifiers and keywords
          [/[a-z_$][\w$]*/, { cases: { '@typeKeywords': 'keyword',
                                       '@keywords': 'keyword',
                                       '@default': 'identifier' } }],
          [/[A-Z][\w\$]*/, 'type.identifier' ],  // to show class names nicely

          // whitespace
          { include: '@whitespace' },

          // delimiters and operators
          [/[{}()\[\]]/, '@brackets'],
          [/[<>](?!@symbols)/, '@brackets'],
          [/@symbols/, { cases: { '@operators': 'operator',
                                  '@default'  : '' } } ],

          // numbers
          [/\d+/, 'number'],

          // strings
          [/"([^"\\]|\\.)*$/, 'string.invalid' ],  // non-teminated string
          [/"/,  { token: 'string.quote', bracket: '@open', next: '@string' } ],
        ],

        comment: [
          [/[^\/*]+/, 'comment' ],
          [/\/\*/,    'comment', '@push' ],    // nested comment
          ["\\*/",    'comment', '@pop'  ],
          [/[\/*]/,   'comment' ]
        ],

        string: [
          [/[^\\"]+/,  'string'],
          [/@escapes/, 'string.escape'],
          [/\\./,      'string.escape.invalid'],
          [/"/,        { token: 'string.quote', bracket: '@close', next: '@pop' } ]
        ],

        whitespace: [
          [/[ \t\r\n]+/, 'white'],
          [/\/\*/,       'comment', '@comment' ],
          [/\/\/.*$/,    'comment'],
        ],
      },
    });

    // Define a new theme that contains only rules that match this language
    monaco.editor.defineTheme('samosaTheme', {
        base: 'vs',
        inherit: false,
        rules: [
            /*{ token: 'custom-info', foreground: '808080' },
            { token: 'custom-error', foreground: 'ff0000', fontStyle: 'bold' },
            { token: 'custom-notice', foreground: 'FFA500' },
            { token: 'custom-date', foreground: '008800' }*/
        ],
        colors: {
            'editor.foreground': '#000000'
        }
    });

    // Register a completion item provider for the new language
    monaco.languages.registerCompletionItemProvider('samosa', {
        provideCompletionItems: () => {
            var suggestions = [
                {
                    label: 'ifelse',
                    kind: monaco.languages.CompletionItemKind.Snippet,
                    insertText: ['if (${1:condition}) {', '\t$0', '} else {', '\t', '}'].join('\n'),
                    insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
                    documentation: 'If-Else Statement'
                },
                {
                    label: 'while',
                    kind: monaco.languages.CompletionItemKind.Snippet,
                    insertText: ['while (${1:condition}) {', '\t$0', '}'].join('\n'),
                    insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
                    documentation: 'While loop'
                },
                {
                    label: 'program',
                    kind: monaco.languages.CompletionItemKind.Snippet,
                    insertText: ['<samosa>\n\n${1}', '\n</samosa>'].join('\n'),
                    insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
                    documentation: 'Samosa program start'
                }
            ];
            return { suggestions: suggestions };
        }
    });
    window.editor = monaco.editor.create(document.getElementById('monacoContainer'), {
    value: `<samosa>

("Hello!") -> putout.

</samosa>`,
    language: 'samosa',
    theme: 'vs',
  });
});


