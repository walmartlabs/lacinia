import urllib

from docutils import nodes, utils
from docutils.parsers.rst import Directive, directives
from docutils.statemachine import ViewList
from typing import Any, Dict, List, Tuple
from docutils.nodes import Element, Node

from sphinx.directives.code import container_wrapper
from six import string_types

from sphinx.locale import _
from sphinx.util import parselinenos
from sphinx.util.nodes import set_source_info
from sphinx.util.docutils import SphinxDirective
from sphinx.config import Config


class RemoteIncludeReader:
    INVALID_OPTIONS_PAIR = [
        ('lineno-match', 'lineno-start'),
        ('lineno-match', 'append'),
        ('lineno-match', 'prepend'),
        ('start-after', 'start-at'),
        ('end-before', 'end-at'),
    ]

    def __init__(self, uri, options: Dict, config: Config) -> None:
        self.uri = uri
        self.options = options
        self.encoding = options.get('encoding', config.source_encoding)
        self.lineno_start = self.options.get('lineno-start', 1)

        self.parse_options()

    def parse_options(self) -> None:
        for option1, option2 in self.INVALID_OPTIONS_PAIR:
            if option1 in self.options and option2 in self.options:
                raise ValueError(__('Cannot use both "%s" and "%s" options') %
                                 (option1, option2))

    def read_file(self, uri: str, location: Tuple[str, int] = None) -> List[str]:
        try:
            with urllib.request.urlopen(uri) as f:
                text = f.read().decode(self.encoding)
                if 'tab-width' in self.options:
                    text = text.expandtabs(self.options['tab-width'])

                return text.splitlines(True)
        except OSError as exc:
            raise OSError(__('Include file %r not found or reading it failed') %
                          uri) from exc
        except UnicodeError as exc:
            raise UnicodeError(__('Encoding %r used for reading included file %r seems to '
                                  'be wrong, try giving an :encoding: option') %
                               (self.encoding, uri)) from exc

    def read(self, location: Tuple[str, int] = None) -> Tuple[str, int]:
        if 'diff' in self.options:
            lines = self.show_diff()
        else:
            filters = [self.start_filter,
                       self.end_filter,
                       self.lines_filter,
                       self.prepend_filter,
                       self.append_filter,
                       self.dedent_filter]
            lines = self.read_file(self.uri, location=location)
            for func in filters:
                lines = func(lines, location=location)

        return ''.join(lines), len(lines)

    def lines_filter(self, lines: List[str], location: Tuple[str, int] = None) -> List[str]:
        linespec = self.options.get('lines')
        if linespec:
            linelist = parselinenos(linespec, len(lines))
            if any(i >= len(lines) for i in linelist):
                logger.warning(__('line number spec is out of range(1-%d): %r') %
                               (len(lines), linespec), location=location)

            if 'lineno-match' in self.options:
                # make sure the line list is not "disjoint".
                first = linelist[0]
                if all(first + i == n for i, n in enumerate(linelist)):
                    self.lineno_start += linelist[0]
                else:
                    raise ValueError(__('Cannot use "lineno-match" with a disjoint '
                                        'set of "lines"'))

            lines = [lines[n] for n in linelist if n < len(lines)]
            if lines == []:
                raise ValueError(__('Line spec %r: no lines pulled from include file %r') %
                                 (linespec, self.uri))

        return lines

    def start_filter(self, lines: List[str], location: Tuple[str, int] = None) -> List[str]:
        if 'start-at' in self.options:
            start = self.options.get('start-at')
            inclusive = False
        elif 'start-after' in self.options:
            start = self.options.get('start-after')
            inclusive = True
        else:
            start = None

        if start:
            for lineno, line in enumerate(lines):
                if start in line:
                    if inclusive:
                        if 'lineno-match' in self.options:
                            self.lineno_start += lineno + 1

                        return lines[lineno + 1:]
                    else:
                        if 'lineno-match' in self.options:
                            self.lineno_start += lineno

                        return lines[lineno:]

            if inclusive is True:
                raise ValueError('start-after pattern not found: %s' % start)
            else:
                raise ValueError('start-at pattern not found: %s' % start)

        return lines

    def end_filter(self, lines: List[str], location: Tuple[str, int] = None) -> List[str]:
        if 'end-at' in self.options:
            end = self.options.get('end-at')
            inclusive = True
        elif 'end-before' in self.options:
            end = self.options.get('end-before')
            inclusive = False
        else:
            end = None

        if end:
            for lineno, line in enumerate(lines):
                if end in line:
                    if inclusive:
                        return lines[:lineno + 1]
                    else:
                        if lineno == 0:
                            pass  # end-before ignores first line
                        else:
                            return lines[:lineno]
            if inclusive is True:
                raise ValueError('end-at pattern not found: %s' % end)
            else:
                raise ValueError('end-before pattern not found: %s' % end)

        return lines

    def prepend_filter(self, lines: List[str], location: Tuple[str, int] = None) -> List[str]:
        prepend = self.options.get('prepend')
        if prepend:
            lines.insert(0, prepend + '\n')

        return lines

    def append_filter(self, lines: List[str], location: Tuple[str, int] = None) -> List[str]:
        append = self.options.get('append')
        if append:
            lines.append(append + '\n')

        return lines

    def dedent_filter(self, lines: List[str], location: Tuple[str, int] = None) -> List[str]:
        if 'dedent' in self.options:
            return dedent_lines(lines, self.options.get('dedent'), location=location)
        else:
            return lines

class RemoteInclude(SphinxDirective):
    """
    Cut-n-paste of LiteralInclude from sphinx 3.2.1; the argument is a URI instead of a local file name.
    Removed the diff and pyobject options.
    """

    has_content = False
    required_arguments = 1
    optional_arguments = 0
    final_argument_whitespace = True
    option_spec = {
        'dedent': int,
        'linenos': directives.flag,
        'lineno-start': int,
        'lineno-match': directives.flag,
        'tab-width': int,
        'language': directives.unchanged_required,
        'force': directives.flag,
        'encoding': directives.encoding,
        'lines': directives.unchanged_required,
        'start-after': directives.unchanged_required,
        'end-before': directives.unchanged_required,
        'start-at': directives.unchanged_required,
        'end-at': directives.unchanged_required,
        'prepend': directives.unchanged_required,
        'append': directives.unchanged_required,
        'emphasize-lines': directives.unchanged_required,
        'caption': directives.unchanged,
        'class': directives.class_option,
        'name': directives.unchanged,
    }

    def run(self) -> List[Node]:
        document = self.state.document
        if not document.settings.file_insertion_enabled:
            return [document.reporter.warning('File insertion disabled',
                                              line=self.lineno)]

        try:
            uri = self.arguments[0]

            location = self.state_machine.get_source_and_line(self.lineno)

            reader = RemoteIncludeReader(uri, self.options, self.config)
            text, lines = reader.read(location=location)

            # rel_filename, filename = self.env.relfn2path(self.arguments[0])
            # self.env.note_dependency(rel_filename)

            retnode = nodes.literal_block(text, text, source=uri)  # type: Element
            retnode['force'] = 'force' in self.options
            self.set_source_info(retnode)
            if 'language' in self.options:
                retnode['language'] = self.options['language']
            if ('linenos' in self.options or 'lineno-start' in self.options or
                    'lineno-match' in self.options):
                retnode['linenos'] = True
            retnode['classes'] += self.options.get('class', [])
            extra_args = retnode['highlight_args'] = {}
            if 'emphasize-lines' in self.options:
                hl_lines = parselinenos(self.options['emphasize-lines'], lines)
                if any(i >= lines for i in hl_lines):
                    logger.warning(__('line number spec is out of range(1-%d): %r') %
                                   (lines, self.options['emphasize-lines']),
                                   location=location)
                extra_args['hl_lines'] = [x + 1 for x in hl_lines if x < lines]
            extra_args['linenostart'] = reader.lineno_start

            if 'caption' in self.options:
                caption = self.options['caption'] or self.arguments[0]
                retnode = container_wrapper(self, retnode, caption)

            # retnode will be note_implicit_target that is linked from caption and numref.
            # when options['name'] is provided, it should be primary ID.
            self.add_name(retnode)

            return [retnode]
        except Exception as exc:
            return [document.reporter.warning(exc, line=self.lineno)]


# This is kind of like a macro for RemoteInclude that figures out the right
# GitHub URL from the two required arguments (tag and path).
class RemoteExample(Directive):

    has_content = False
    required_arguments = 2  # tag and path
    optional_arguments = 1

    option_spec = RemoteInclude.option_spec

    def run(self):
        tag = self.arguments[0]
        path = self.arguments[1]
        url = 'https://raw.githubusercontent.com/walmartlabs/clojure-game-geek/' + tag + '/' + path

        content = [".. remoteinclude:: " + url]

        for k, v in self.options.items():
            if v is None:
                content += ['  :' + k + ':']
            else:
                content += ['  :' + k + ': ' + v]

        if 'language' not in self.options:
            content += ['  :language: clojure']

        if 'caption' not in self.options:
            content += ['  :caption: ' + path]

        vl = ViewList(content, source='')
        node = nodes.Element()

        self.state.nested_parse(vl, 0, node)

        return node.children

def api_link_inner(baseurl, rawtext, text, options):

  package, varname =  text.strip().split( "/")

  if package == '':
    package = 'com.walmartlabs.lacinia'
  else:
    package = 'com.walmartlabs.lacinia.' + package

  ref = '%s/%s.html#var-%s' % (baseurl, package, varname)
  title = '%s/%s' % (package, varname)
  node = nodes.reference(rawtext, utils.unescape(title), refuri=ref, **options)

  return [node], []


def api_link_role(role, rawtext, text, lineno, inliner, options={}, content=[]):

  return api_link_inner('http://walmartlabs.github.io/apidocs/lacinia', rawtext, text, options)

def setup(app):
    app.add_directive('remoteinclude', RemoteInclude)
    app.add_directive('ex', RemoteExample)
    app.add_role('api', api_link_role)

    return {
        'version': '0.1',
        'parallel_read_safe': True,
        'parallel_write_safe': True,
    }
