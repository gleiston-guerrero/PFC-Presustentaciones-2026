package ec.edu.uteq.presustentaciones.services;

import ec.edu.uteq.presustentaciones.dto.SaveResourceRequest;
import ec.edu.uteq.presustentaciones.dto.ResourceTitulacionDTO;
import ec.edu.uteq.presustentaciones.entities.Program;
import ec.edu.uteq.presustentaciones.entities.ResourceTitulacion;
import ec.edu.uteq.presustentaciones.repositories.ProgramRepository;
import ec.edu.uteq.presustentaciones.repositories.ResourceTitulacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResourceTitulacionServiceImpl implements ResourceTitulacionService {

    private final ResourceTitulacionRepository resourceRepository;
    private final ProgramRepository programRepository;

    /**
     * Resources visibles para una program (los generales + los de esa program).
     *
     * @param programId id de la program a filtrar, o {@code null} para list todos
     * @return los resources de titulación visibles
     */
    @Override
    @Transactional(readOnly = true)
    public List<ResourceTitulacionDTO> list(Integer programId) {
        List<ResourceTitulacion> resources = programId == null
                ? resourceRepository.listTodos()
                : resourceRepository.listVisiblesParaProgram(programId);
        return resources.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    /**
     * @param request datos del resource a create
     * @return el resource creado
     * @throws IllegalArgumentException si la program indicada no existe
     */
    @Override
    @Transactional
    public ResourceTitulacionDTO create(SaveResourceRequest request) {
        ResourceTitulacion resource = ResourceTitulacion.builder()
                .titulo(request.getTitulo().trim())
                .categoria(request.getCategoria().trim())
                .urlArchivo(request.getUrlArchivo().trim())
                .program(resolveProgram(request.getProgramId()))
                .build();
        return mapToDTO(resourceRepository.save(resource));
    }

    /**
     * @param id      id del resource a update
     * @param request datos nuevos del resource
     * @return el resource actualizado
     * @throws IllegalArgumentException si el resource o la program indicada no existen
     */
    @Override
    @Transactional
    public ResourceTitulacionDTO update(Integer id, SaveResourceRequest request) {
        ResourceTitulacion resource = resourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Recurso no encontrado"));
        resource.setTitulo(request.getTitulo().trim());
        resource.setCategoria(request.getCategoria().trim());
        resource.setUrlArchivo(request.getUrlArchivo().trim());
        resource.setProgram(resolveProgram(request.getProgramId()));
        return mapToDTO(resourceRepository.save(resource));
    }

    /**
     * @param id id del resource a delete
     * @throws IllegalArgumentException si el resource no existe
     */
    @Override
    @Transactional
    public void delete(Integer id) {
        if (!resourceRepository.existsById(id)) {
            throw new IllegalArgumentException("Recurso no encontrado");
        }
        resourceRepository.deleteById(id);
    }

    private Program resolveProgram(Integer programId) {
        if (programId == null) {
            return null;
        }
        return programRepository.findById(programId)
                .orElseThrow(() -> new IllegalArgumentException("Carrera no encontrada"));
    }

    private ResourceTitulacionDTO mapToDTO(ResourceTitulacion r) {
        return ResourceTitulacionDTO.builder()
                .id(r.getId())
                .titulo(r.getTitulo())
                .categoria(r.getCategoria())
                .urlArchivo(r.getUrlArchivo())
                .programId(r.getProgram() != null ? r.getProgram().getId() : null)
                .programNombre(r.getProgram() != null ? r.getProgram().getNombre() : null)
                .build();
    }
}
