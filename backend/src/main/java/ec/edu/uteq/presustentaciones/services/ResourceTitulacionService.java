package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.SaveResourceRequest;
import ec.edu.uteq.presustentaciones.dto.ResourceTitulacionDTO;

import java.util.List;

public interface ResourceTitulacionService {

    /** Resources visibles para una program (los generales + los de esa program). null = todos. */
    List<ResourceTitulacionDTO> list(Integer programId);

    ResourceTitulacionDTO create(SaveResourceRequest request);

    ResourceTitulacionDTO update(Integer id, SaveResourceRequest request);

    void delete(Integer id);
}
