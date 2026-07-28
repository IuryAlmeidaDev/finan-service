package dev.iury.lifeos.finance.tag;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import dev.iury.lifeos.finance.model.Tag;
import dev.iury.lifeos.finance.model.TransactionTag;
import dev.iury.lifeos.finance.model.TransactionTagId;
import dev.iury.lifeos.finance.repository.TagRepository;

@ApplicationScoped
public class TagService {

    @Inject TagRepository tags;
    @Inject EntityManager em;

    @Transactional
    public Tag create(String name, String color) {
        long count = tags.count("lower(name)", name.toLowerCase());
        if (count > 0) {
            throw new IllegalArgumentException("Tag with this name already exists");
        }
        
        Tag tag = new Tag();
        tag.name = name;
        tag.color = color;
        tags.persist(tag);
        return tag;
    }

    @Transactional
    public void delete(UUID id) {
        Tag tag = tags.findByIdOptional(id)
            .orElseThrow(() -> new IllegalArgumentException("Tag not found"));
            
        em.createQuery("delete from TransactionTag t where t.tag.id = :tagId")
            .setParameter("tagId", id)
            .executeUpdate();
            
        tags.delete(tag);
    }

    @Transactional
    public void setTransactionTags(UUID transactionId, List<UUID> tagIds) {
        if (tagIds != null && tagIds.size() > 15) {
            throw new IllegalArgumentException("Maximum of 15 tags per transaction exceeded");
        }
        
        em.createQuery("delete from TransactionTag t where t.transaction.id = :txId")
            .setParameter("txId", transactionId)
            .executeUpdate();
            
        if (tagIds != null) {
            for (UUID tagId : tagIds) {
                TransactionTag tt = new TransactionTag();
                tt.transaction = em.find(dev.iury.lifeos.finance.model.FinancialTransaction.class, transactionId);
                tt.tag = em.find(Tag.class, tagId);
                em.persist(tt);
            }
        }
    }

    public List<Tag> getTransactionTags(UUID transactionId) {
        return em.createQuery("select t from Tag t join TransactionTag tt on t.id = tt.tag.id where tt.transaction.id = :txId", Tag.class)
            .setParameter("txId", transactionId)
            .getResultList();
    }
}
