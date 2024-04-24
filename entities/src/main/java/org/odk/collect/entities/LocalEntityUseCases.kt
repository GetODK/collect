package org.odk.collect.entities

import org.javarosa.core.model.instance.CsvExternalInstance
import java.io.File

object LocalEntityUseCases {

    fun updateLocalEntities(
        dataset: String,
        entityList: File,
        entitiesRepository: EntitiesRepository
    ) {
        val root = CsvExternalInstance.parse(dataset, entityList.absolutePath)
        val items = root.getChildrenWithName("item")
        items.forEach { item ->
            val id = item.getFirstChild("name")?.value?.value as? String
            val label = item.getFirstChild("label")?.value?.value as? String
            val version = (item.getFirstChild("__version")?.value?.value as? String)?.toInt()
            if (id == null || label == null || version == null) {
                return
            }

            val existing = entitiesRepository.get(dataset, id)
            if (existing == null || existing.version < version) {
                val entity = Entity(
                    dataset,
                    id,
                    label,
                    version
                )

                entitiesRepository.save(entity)
            } else {
                val properties = 0.until(item.numChildren)
                    .fold(emptyList<Pair<String, String>>()) { properties, index ->
                        val child = item.getChildAt(index)

                        if (!listOf(
                                EntityItemElement.ID,
                                EntityItemElement.LABEL,
                                EntityItemElement.VERSION
                            ).contains(child.name)
                        ) {
                            properties + Pair(child.name, child.value!!.value as String)
                        } else {
                            properties
                        }
                    }

                val entity = existing.copy(properties = properties)
                entitiesRepository.save(entity)
            }
        }
    }
}