INSERT INTO category (id, name, type, is_system, sort_order) VALUES
    (gen_random_uuid(), 'Salário', 'INCOME', true, 10),
    (gen_random_uuid(), 'Freelance', 'INCOME', true, 20),
    (gen_random_uuid(), 'Rendimentos', 'INCOME', true, 30),
    (gen_random_uuid(), 'Cashback', 'INCOME', true, 40),
    (gen_random_uuid(), 'Presentes', 'INCOME', true, 50),
    (gen_random_uuid(), 'Outros', 'INCOME', true, 60),
    (gen_random_uuid(), 'Restaurantes/Delivery', 'EXPENSE', true, 70),
    (gen_random_uuid(), 'Viagens', 'EXPENSE', true, 80),
    (gen_random_uuid(), 'Ajuste de Saldo', 'EXPENSE', true, 90),
    (gen_random_uuid(), 'Não Categorizado', 'EXPENSE', true, 100);

WITH parent(name, sort_order) AS (
    VALUES
        ('Moradia', 110),
        ('Alimentação', 120),
        ('Transporte', 130),
        ('Saúde', 140),
        ('Educação', 150),
        ('Lazer', 160),
        ('Compras', 170),
        ('Cuidados Pessoais', 180),
        ('Assinaturas', 190)
)
INSERT INTO category (id, name, type, is_system, sort_order)
SELECT gen_random_uuid(), name, 'EXPENSE', true, sort_order
FROM parent;

WITH child(parent_name, name, sort_order) AS (
    VALUES
        ('Moradia', 'Aluguel', 1),
        ('Moradia', 'Condomínio', 2),
        ('Moradia', 'Luz', 3),
        ('Moradia', 'Água', 4),
        ('Moradia', 'Gás', 5),
        ('Moradia', 'Internet', 6),
        ('Alimentação', 'Supermercado', 1),
        ('Alimentação', 'Feira', 2),
        ('Transporte', 'Combustível', 1),
        ('Transporte', 'Transporte Público', 2),
        ('Transporte', 'Uber/99', 3),
        ('Transporte', 'Estacionamento', 4),
        ('Transporte', 'Manutenção Veicular', 5),
        ('Transporte', 'IPVA', 6),
        ('Saúde', 'Plano de Saúde', 1),
        ('Saúde', 'Farmácia', 2),
        ('Saúde', 'Consultas', 3),
        ('Educação', 'Mensalidade', 1),
        ('Educação', 'Cursos', 2),
        ('Educação', 'Material', 3),
        ('Lazer', 'Cinema', 1),
        ('Lazer', 'Shows', 2),
        ('Lazer', 'Jogos', 3),
        ('Compras', 'Roupas', 1),
        ('Compras', 'Eletrônicos', 2),
        ('Cuidados Pessoais', 'Academia', 1),
        ('Cuidados Pessoais', 'Barbearia/Salão', 2),
        ('Assinaturas', 'Netflix', 1),
        ('Assinaturas', 'Spotify', 2)
)
INSERT INTO category (
    id, name, type, parent_category_id, is_system, sort_order
)
SELECT gen_random_uuid(), child.name, 'EXPENSE', parent.id, true, child.sort_order
FROM child
JOIN category parent ON parent.name = child.parent_name
WHERE parent.parent_category_id IS NULL;
