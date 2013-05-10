package de.ecspride.sourcesinkfinder.features;

import java.util.ArrayList;
import java.util.List;

import soot.Body;
import soot.Local;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.AssignStmt;
import soot.jimple.FieldRef;
import soot.jimple.IdentityStmt;
import soot.jimple.InstanceFieldRef;
import soot.jimple.ReturnStmt;
import soot.jimple.infoflow.android.data.AndroidMethod;

/**
 * Feature that checks whether the current method begins with "get", and there
 * is a corresponding "set" method in the class.
 *
 * @author Steven Arzt, Siegfried Rasthofer
 *
 */
public class MethodIsGetterNoSetterFeature extends AbstractSootFeature {

	public MethodIsGetterNoSetterFeature(String mapsJAR, String androidJAR) {
		super(mapsJAR, androidJAR);
	}
	
	@Override
	public Type appliesInternal(AndroidMethod method) {
		SootMethod sm = getSootMethod(method);
		
		// We are only interested in getters and setters
		if (!sm.getName().startsWith("get")
				&& !sm.getName().startsWith("set"))
			return Type.NOT_SUPPORTED;
		String baseName = sm.getName().substring(3);
		String getterName = "get" + baseName;
		String setterName = "set" + baseName;

		try {
			// Find the getter and the setter
			SootMethod getter = getSootMethod(new AndroidMethod
					(getterName, "", sm.getDeclaringClass().getName()));
			SootMethod setter = getSootMethod(new AndroidMethod
					(setterName, "", sm.getDeclaringClass().getName()));
			if (getter == null || setter == null)
				return Type.FALSE;
			
			if (!setter.isConcrete() || !getter.isConcrete())
				return Type.NOT_SUPPORTED;

			Body bodyGetter = null;
			Body bodySetter = null;
			try{
				bodyGetter = getter.retrieveActiveBody();
				bodySetter = setter.retrieveActiveBody();
			}catch(Exception ex){
				return Type.NOT_SUPPORTED;
			}
			
			// Find the local that gets returned
			Local returnLocal = null;
			for (Unit u : bodyGetter.getUnits())
				if (u instanceof ReturnStmt) {
					ReturnStmt ret = (ReturnStmt) u;
					if (ret.getOp() instanceof Local) {
						returnLocal = (Local) ret.getOp();
						break;
					}
				}
			if (returnLocal == null)
				return Type.FALSE;
			
			// Find where the local is assigned a value in the code
			List<FieldRef> accessPath = new ArrayList<FieldRef>();
			Local returnBase = returnLocal;
			while (returnBase != null)
				for (Unit u : bodyGetter.getUnits()) {
					if (u instanceof AssignStmt) {
						AssignStmt assign = (AssignStmt) u;
						if (assign.getLeftOp().equals(returnBase))
							if (assign.getRightOp() instanceof InstanceFieldRef) {
								InstanceFieldRef ref = (InstanceFieldRef) assign.getRightOp();
								accessPath.add(0, ref);
								returnBase = (Local) ref.getBase();
								break;
							}
							else
								returnBase = null;
					}
					else if (u instanceof IdentityStmt) {
						IdentityStmt id = (IdentityStmt) u;
						if (id.getLeftOp().equals(returnBase))
							returnBase = null;
					}
				}
			if (accessPath.isEmpty())
				return Type.FALSE;
			/*
			// Find the corresponding access path in the setter
			for (Unit u : bodySetter.getUnits())
				if (u instanceof AssignStmt) {
					AssignStmt assign = (AssignStmt) u;
					if (assign.getLeftOp() instanceof InstanceFieldRef
							&& assign.getRightOp() instanceof Local) {
						InstanceFieldRef iref = (InstanceFieldRef) assign.getLeftOp();
						if (iref.getFieldRef().toString().equals(accessPath.get(accessPath.size() - 1).getFieldRef().toString())) {
							// This is a starting point
							boolean pathFound = false;
							Local startLocal = (Local) iref.getBase();
							int accessPathPos = accessPath.size() - 2;
							while (startLocal != null) {
								for (Unit u2 : bodySetter.getUnits()) {
									if (u2 instanceof AssignStmt) {
										AssignStmt assign2 = (AssignStmt) u2;
										if (assign2.getLeftOp().equals(startLocal))
											if (assign2.getRightOp() instanceof InstanceFieldRef) {
												InstanceFieldRef ref = (InstanceFieldRef) assign2.getRightOp();
												if (accessPath.get(accessPathPos--).getFieldRef().toString().equals(ref.getFieldRef().toString())) {
													startLocal = (Local) ref.getBase();
													break;
												}
												else
													startLocal = null;
											}
											else
												startLocal = null;
									}
									else if (u2 instanceof IdentityStmt) {
										IdentityStmt id = (IdentityStmt) u2;
										if (id.getLeftOp().equals(startLocal)) {
											startLocal = null;
											pathFound = true;
											break;
										}
									}
								}	
							}
							
							if (pathFound) {
								if (assign.getRightOp() instanceof Local) {
									// Find the parameter being set
									for (Unit u2 : bodySetter.getUnits())
										if (u2 instanceof IdentityStmt) {
											IdentityStmt id = (IdentityStmt) u2;
											if (id.getLeftOp().equals(assign.getRightOp()))
												return Type.TRUE;
										}
								}
								break;
							}
						}
					}
				}
			return Type.FALSE;
			*/
			return Type.TRUE;
		}catch (Exception ex) {
			System.err.println("Something went wrong:");
			ex.printStackTrace();
			return Type.NOT_SUPPORTED;
		}
	}
	
	@Override
	public String toString() {
		return "<Method is lone getter or setter>";
	}

}
