package com.hr.commun.societe;

import java.util.List;
import java.util.UUID;

/**
 * Fournit la liste des identifiants des sociétés actives (utilisée par les planificateurs).
 */
public interface SocietesActives {

	List<UUID> lister();
}
