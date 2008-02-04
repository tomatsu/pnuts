package pnuts.lang;

/**
 * The system property "pnuts.package.factory" is specified at startup time, the
 * package(..) builtin function calls its createPackage() method of the
 * specified class.
 * 
 * @see pnuts.lang.Package
 * @version 1.1
 */
public interface PackageFactory {
	public Package createPackage(String pkgName, Package parent);
}