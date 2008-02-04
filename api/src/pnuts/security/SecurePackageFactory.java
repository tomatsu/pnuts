package pnuts.security;

import pnuts.lang.Package;
import pnuts.lang.PackageFactory;

/**
 * A Package Factory that creates SecurePackage
 */
public class SecurePackageFactory implements PackageFactory {
	public Package createPackage(String pkgName, Package parent){
		return new SecurePackage(pkgName, parent);
	}
}
