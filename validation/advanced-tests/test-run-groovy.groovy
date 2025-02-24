// simple test to make sure that `run-groovy` is working

// check environment variables
[ 'JYPATH', 'JAVA_OPTS' ].collectEntries{ [it, System.getenv(it)] }.each{ name, val ->
  if(val==null) {
    System.err.println "ERROR: environment variable $name not set"
    System.exit(100)
  }
  if(val=="") {
    System.err.println "ERROR: environment variable $name is set, but empty"
    System.exit(100)
  }
  System.out.println "$name = $val"
}

// try to import a local package
import org.jlab.clas.physics.*

System.out.println "TEST PASSED"
