from docutils import nodes, utils


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
    app.add_role('api', api_link_role)

    return {
        'version': '0.1',
        'parallel_read_safe': True,
        'parallel_write_safe': True,
    }
