# 家具目录生成器：改这里 → 跑一遍 → 生成 models/*.json + furniture.yml
# 用法：powershell -File tools/gen-catalog.ps1
# 坐标约定：0~16 = 1 格（>16 就是多格高/宽），y=0 贴地，正面朝 +z。
# 盒子写法：x1,y1,z1,x2,y2,z2,贴图键

$root = "G:\插件\家具"
$modelDir = Join-Path $root "src\main\resources\pack\assets\furniture\models"
$yamlPath = Join-Path $root "src\main\resources\furniture.yml"

function New-Box([string]$spec) {
    $p = $spec.Split(',')
    $faces = [ordered]@{}
    foreach ($dir in @('down', 'up', 'north', 'south', 'west', 'east')) {
        $faces[$dir] = [ordered]@{ texture = "#$($p[6])" }
    }
    return [ordered]@{
        from  = @([int]$p[0], [int]$p[1], [int]$p[2])
        to    = @([int]$p[3], [int]$p[4], [int]$p[5])
        faces = $faces
    }
}

$M = @{
    OAK    = 'minecraft:block/oak_planks'
    SPRUCE = 'minecraft:block/spruce_planks'
    BIRCH  = 'minecraft:block/birch_planks'
    DARK   = 'minecraft:block/dark_oak_planks'
    STR_OAK = 'minecraft:block/stripped_oak_log'
    STR_SPRUCE = 'minecraft:block/stripped_spruce_log'
    STR_DARK = 'minecraft:block/stripped_dark_oak_log'
    LOG_OAK = 'minecraft:block/oak_log'
    IRON   = 'minecraft:block/iron_block'
    STONE  = 'minecraft:block/stone_bricks'
    COBBLE = 'minecraft:block/cobblestone'
    QUARTZ = 'minecraft:block/smooth_quartz'
    BRICK  = 'minecraft:block/bricks'
    WHITE  = 'minecraft:block/white_wool'
    WHITE_CON = 'minecraft:block/white_concrete'
    WHITE_CONCRETE = 'minecraft:block/white_concrete'
    RED    = 'minecraft:block/red_wool'
    BLUE   = 'minecraft:block/blue_wool'
    GREEN  = 'minecraft:block/green_wool'
    BLACK  = 'minecraft:block/black_concrete'
    GLASS  = 'minecraft:block/glass'
    BOOK   = 'minecraft:block/bookshelf'
    LEAF   = 'minecraft:block/oak_leaves'
    MOSS   = 'minecraft:block/moss_block'
    DIRT   = 'minecraft:block/dirt'
    POT    = 'minecraft:block/terracotta'
    GLOW   = 'minecraft:block/glowstone'
    HAY    = 'minecraft:block/hay_block_side'
    PUMPKIN = 'minecraft:block/carved_pumpkin'
    WATER  = 'minecraft:block/water_still'
    CHERRY = 'minecraft:block/cherry_leaves'
    CHERRY_P = 'minecraft:block/cherry_planks'
    GLASS_T = 'minecraft:block/glass'
    SAND   = 'minecraft:block/sand'
}

# 可选字段（不加就是默认）：
#   sw      占地宽度（格，模型局部 +x 方向），多格家具用 2/3
#   sd      占地深度（格，模型局部 +z 方向）
#   seat    能不能坐人（true/false）
#   seatY   坐姿高度（相对脚下方块底部，格）
#   light   能不能开关灯（模型发亮/变暗）

# ------------------------------------------------------------------ 目录
$items = @(

# ================= 座椅 =================
@{ id='chair'; name='木椅'; cat='座椅'; hw=0.85; hh=1.15; skipModel=$true; seat=$true; seatY=0.62;
   tex=[ordered]@{ P=$M.OAK; L=$M.STR_OAK };
   recipe=@{ shaped=$true; shape=@(' P ', 'PPP'); ing=[ordered]@{ P='OAK_PLANKS' }; amount=1 } },

@{ id='chair_spruce'; name='云杉椅'; cat='座椅'; hw=0.85; hh=1.3; seat=$true; seatY=0.62;
   tex=[ordered]@{ P=$M.SPRUCE; L=$M.STR_SPRUCE };
   box=@('2,8,2,14,10,14,P', '2,0,2,4,8,4,L', '12,0,2,14,8,4,L', '2,0,12,4,8,14,L', '12,0,12,14,8,14,L', '2,10,2,14,20,4,L');
   recipe=@{ shaped=$true; shape=@('SS ', 'SSS'); ing=[ordered]@{ S='SPRUCE_PLANKS' }; amount=1 } },

@{ id='stool'; name='圆凳'; cat='座椅'; hw=0.8; hh=1.0; skipModel=$true; seat=$true; seatY=0.5;
   tex=[ordered]@{ P=$M.OAK; L=$M.STR_OAK };
   recipe=@{ shaped=$false; mats=@('OAK_PLANKS', 'OAK_PLANKS', 'STICK', 'STICK'); amount=1 } },

@{ id='bench'; name='长凳'; cat='座椅'; hw=1.0; hh=1.2; seat=$true; seatY=0.62;
   tex=[ordered]@{ P=$M.OAK; L=$M.STR_OAK };
   box=@('1,8,1,15,10,15,P', '1,10,1,15,16,3,P', '2,0,2,4,8,4,L', '12,0,2,14,8,4,L', '2,0,12,4,8,14,L', '12,0,12,14,8,14,L');
   recipe=@{ shaped=$true; shape=@('PPP', 'I I'); ing=[ordered]@{ P='OAK_PLANKS'; I='IRON_INGOT' }; amount=1 } },

@{ id='rocking_chair'; name='摇椅'; cat='座椅'; hw=0.9; hh=1.3; seat=$true; seatY=0.62;
   tex=[ordered]@{ P=$M.DARK; L=$M.STR_DARK };
   box=@('3,8,3,13,10,13,P', '3,10,2,13,20,4,P', '3,0,10,5,8,14,L', '11,0,10,13,8,14,L', '2,0,2,14,2,16,L', '2,6,3,14,8,5,L');
   recipe=@{ shaped=$true; shape=@(' P ', 'PPP', 'L L'); ing=[ordered]@{ P='DARK_OAK_PLANKS'; L='STICK' }; amount=1 } },

@{ id='armchair'; name='扶手椅'; cat='座椅'; hw=1.0; hh=1.2; seat=$true; seatY=0.42;
   tex=[ordered]@{ G=$M.GREEN; W=$M.WHITE; P=$M.OAK };
   box=@('0,2,2,16,6,16,G', '0,6,2,3,11,16,G', '13,6,2,16,11,16,G', '0,6,2,16,16,6,G', '3,6,6,13,8,15,W', '1,0,3,3,2,5,P', '13,0,3,15,2,5,P');
   recipe=@{ shaped=$true; shape=@('G G', 'GGG', 'P P'); ing=[ordered]@{ G='GREEN_WOOL'; P='OAK_PLANKS' }; amount=1 } },

@{ id='couch'; name='沙发'; cat='座椅'; hw=1.0; hh=1.0; skipModel=$true; seat=$true; seatY=0.45;
   tex=[ordered]@{ R=$M.RED; P=$M.OAK };
   recipe=@{ shaped=$true; shape=@('PPP', 'WWW', 'L L'); ing=[ordered]@{ P='OAK_PLANKS'; W='RED_WOOL'; L='STICK' }; amount=1 } },

@{ id='couch_blue'; name='蓝色沙发'; cat='座椅'; hw=1.0; hh=1.2; seat=$true; seatY=0.35;
   tex=[ordered]@{ B=$M.BLUE; W=$M.WHITE; P=$M.OAK };
   box=@('0,0,1,16,4,15,B', '0,4,1,16,13,5,B', '0,4,5,3,9,15,B', '13,4,5,16,9,15,B', '3,4,6,7,7,14,W', '9,4,6,13,7,14,W', '1,0,2,3,2,4,P', '13,0,2,15,2,4,P');
   recipe=@{ shaped=$true; shape=@('BBB', 'BPB', 'PPP'); ing=[ordered]@{ B='BLUE_WOOL'; P='OAK_PLANKS' }; amount=1 } },

@{ id='office_chair'; name='办公椅'; cat='座椅'; hw=0.8; hh=1.2; seat=$true; seatY=0.65;
   tex=[ordered]@{ K=$M.BLACK; I=$M.IRON };
   box=@('7,0,7,9,2,9,I', '4,2,4,12,4,12,I', '4,8,4,12,10,12,K', '4,10,3,12,20,5,K', '7,4,7,9,8,9,I', '10,8,5,12,9,11,K');
   recipe=@{ shaped=$true; shape=@('I I', 'III', ' L '); ing=[ordered]@{ I='IRON_INGOT'; L='STICK' }; amount=1 } },

@{ id='bar_stool'; name='吧台凳'; cat='座椅'; hw=0.7; hh=1.5; seat=$true; seatY=0.92;
   tex=[ordered]@{ I=$M.IRON; D=$M.DARK; S=$M.STR_DARK };
   box=@('5,0,5,11,2,11,I', '7,2,7,9,24,9,I', '4,24,4,12,27,12,D', '4,25,4,12,26,12,S', '5,12,5,11,13,11,I');
   recipe=@{ shaped=$true; shape=@(' I ', 'III', 'SSS'); ing=[ordered]@{ I='IRON_INGOT'; S='STICK' }; amount=1 } },

@{ id='piano_stool'; name='琴凳'; cat='座椅'; hw=0.9; hh=0.8; seat=$true; seatY=0.5;
   tex=[ordered]@{ D=$M.DARK; W=$M.WHITE };
   box=@('1,10,1,15,12,15,D', '2,12,2,14,13,14,W', '2,0,2,4,10,4,D', '12,0,2,14,10,4,D', '2,0,12,4,10,14,D', '12,0,12,14,10,14,D');
   recipe=@{ shaped=$true; shape=@(' D ', 'D D', ' D '); ing=[ordered]@{ D='DARK_OAK_PLANKS' }; amount=1 } },

@{ id='sofa_long'; name='长条沙发'; cat='座椅'; hw=1.0; hh=1.0; sw=2; seat=$true; seatY=0.35; seats=2;
   tex=[ordered]@{ R=$M.RED; W=$M.WHITE; P=$M.OAK };
   box=@('0,0,2,32,4,14,R', '0,4,2,32,14,5,R', '0,4,5,3,9,14,R', '29,4,5,32,9,14,R', '3,4,6,15,7,14,W', '17,4,6,29,7,14,W', '1,0,2,3,3,4,P', '29,0,2,31,3,4,P');
   recipe=@{ shaped=$true; shape=@('RRR', 'RRR', 'P P'); ing=[ordered]@{ R='RED_WOOL'; P='OAK_PLANKS' }; amount=1 } },

@{ id='bean_bag'; name='懒人沙发'; cat='座椅'; hw=1.0; hh=0.8; seat=$true; seatY=0.3;
   tex=[ordered]@{ G=$M.GREEN; W=$M.WHITE };
   box=@('0,0,3,16,7,13,G', '1,7,4,15,12,12,G', '2,12,5,14,14,11,W');
   recipe=@{ shaped=$false; mats=@('GREEN_WOOL', 'GREEN_WOOL', 'GREEN_WOOL', 'WHITE_WOOL'); amount=1 } },

# ================= 桌台 =================
@{ id='table'; name='木桌'; cat='桌台'; hw=1.0; hh=0.95; skipModel=$true;
   tex=[ordered]@{ P=$M.OAK; L=$M.STR_OAK };
   recipe=@{ shaped=$true; shape=@('PPP', ' L ', ' L '); ing=[ordered]@{ P='OAK_PLANKS'; L='STICK' }; amount=1 } },

@{ id='coffee_table'; name='茶几'; cat='桌台'; hw=1.0; hh=0.7;
   tex=[ordered]@{ D=$M.DARK; L=$M.STR_DARK };
   box=@('0,10,2,16,12,14,D', '1,0,3,3,10,5,L', '13,0,3,15,10,5,L', '1,2,4,15,3,12,D');
   recipe=@{ shaped=$true; shape=@('PPP', 'L L'); ing=[ordered]@{ P='DARK_OAK_PLANKS'; L='STICK' }; amount=1 } },

@{ id='desk'; name='书桌'; cat='桌台'; hw=1.0; hh=1.2;
   tex=[ordered]@{ P=$M.OAK; D=$M.DARK; I=$M.IRON };
   box=@('0,12,0,16,14,16,P', '0,0,0,16,12,3,P', '1,2,3,15,10,4,D', '7,5,4,9,6,5,I', '0,0,13,3,12,16,P', '13,0,13,16,12,16,P');
   recipe=@{ shaped=$true; shape=@('PPP', 'P P', 'P P'); ing=[ordered]@{ P='OAK_PLANKS' }; amount=1 } },

@{ id='dining_table'; name='餐桌'; cat='桌台'; hw=1.0; hh=0.95; sw=2;
   tex=[ordered]@{ D=$M.DARK; L=$M.STR_DARK };
   box=@('0,13,2,32,15,14,D', '2,0,3,4,13,5,L', '28,0,3,30,13,5,L', '2,0,11,4,13,13,L', '28,0,11,30,13,13,L');
   recipe=@{ shaped=$true; shape=@('PPP', ' L ', ' L '); ing=[ordered]@{ P='DARK_OAK_PLANKS'; L='STICK' }; amount=1 } },

@{ id='side_table'; name='边几'; cat='桌台'; hw=0.8; hh=0.9;
   tex=[ordered]@{ P=$M.BIRCH; L=$M.STR_OAK };
   box=@('2,11,2,14,13,14,P', '2,0,2,4,11,4,L', '12,0,2,14,11,4,L', '2,0,12,4,11,14,L', '12,0,12,14,11,14,L');
   recipe=@{ shaped=$false; mats=@('BIRCH_PLANKS', 'BIRCH_PLANKS', 'STICK'); amount=1 } },

@{ id='picnic_table'; name='野餐桌'; cat='桌台'; hw=1.0; hh=1.0; sw=2; seat=$true; seatY=0.5; seats=2;
   tex=[ordered]@{ P=$M.SPRUCE; L=$M.STR_SPRUCE };
   box=@('0,12,4,32,14,12,P', '0,6,0,32,8,3,P', '0,6,13,32,8,16,P', '2,0,4,4,12,6,L', '28,0,4,30,12,6,L', '2,0,10,4,12,12,L', '28,0,10,30,12,12,L');
   recipe=@{ shaped=$true; shape=@('PPP', 'PPP', 'L L'); ing=[ordered]@{ P='SPRUCE_PLANKS'; L='STICK' }; amount=1 } },

# ================= 储物 =================
@{ id='round_table'; name='圆桌'; cat='桌台'; hw=1.0; hh=0.95;
   tex=[ordered]@{ D=$M.DARK; L=$M.STR_DARK; Q=$M.QUARTZ };
   box=@('0,12,1,16,14,15,D', '6,0,6,10,12,10,L', '1,4,2,5,12,14,L', '11,4,2,12,12,14,L');
   recipe=@{ shaped=$true; shape=@('QQQ', ' L '); ing=[ordered]@{ Q='DARK_OAK_PLANKS'; L='STICK' }; amount=1 } },

@{ id='bar_counter'; name='吧台'; cat='桌台'; hw=1.0; hh=1.1; sw=2;
   tex=[ordered]@{ Q=$M.QUARTZ; D=$M.DARK; K=$M.BLACK };
   box=@('0,0,2,32,13,13,D', '0,13,1,32,16,14,Q', '0,0,1,32,2,14,K', '1,4,13,31,10,14,D');
   recipe=@{ shaped=$true; shape=@('QQQ', 'DDD', 'D D'); ing=[ordered]@{ Q='SMOOTH_QUARTZ'; D='DARK_OAK_PLANKS' }; amount=1 } },

@{ id='computer_desk'; name='电脑桌'; cat='桌台'; hw=1.0; hh=1.0;
   tex=[ordered]@{ D=$M.DARK; K=$M.BLACK; G=$M.GLOW; I=$M.IRON };
   box=@('0,13,2,16,15,16,D', '1,0,2,3,13,4,D', '13,0,2,15,13,4,D', '1,4,3,15,12,14,D', '5,15,5,11,27,7,K', '6,17,6,10,25,7,G', '7,0,8,9,1,10,I');
   recipe=@{ shaped=$true; shape=@('DDD', 'D D', 'D D'); ing=[ordered]@{ D='DARK_OAK_PLANKS' }; amount=1 } },

@{ id='workbench_table'; name='工具台'; cat='桌台'; hw=1.0; hh=1.0; sw=2;
   tex=[ordered]@{ P=$M.OAK; S=$M.SPRUCE; D=$M.DARK };
   box=@('0,14,0,32,16,16,P', '0,0,2,32,14,14,S', '2,15,2,14,16,14,D', '0,0,2,32,2,14,D');
   recipe=@{ shaped=$true; shape=@('PPP', 'PSP', 'PPP'); ing=[ordered]@{ P='OAK_PLANKS'; S='STICK' }; amount=1 } },

@{ id='cabinet'; name='木柜'; cat='储物'; hw=1.0; hh=1.0; skipModel=$true;
   tex=[ordered]@{ S=$M.SPRUCE; D=$M.DARK; I=$M.IRON };
   recipe=@{ shaped=$true; shape=@('SSS', 'SIS', 'SSS'); ing=[ordered]@{ S='SPRUCE_PLANKS'; I='IRON_INGOT' }; amount=1 } },

@{ id='drawer'; name='抽屉柜'; cat='储物'; hw=1.0; hh=1.0; skipModel=$true;
   tex=[ordered]@{ S=$M.SPRUCE; D=$M.DARK; I=$M.IRON };
   recipe=@{ shaped=$true; shape=@('SSS', 'I I', 'SSS'); ing=[ordered]@{ S='SPRUCE_PLANKS'; I='IRON_INGOT' }; amount=1 } },

@{ id='shelf'; name='书架'; cat='储物'; hw=1.0; hh=1.0; skipModel=$true;
   tex=[ordered]@{ P=$M.OAK; B=$M.BOOK };
   recipe=@{ shaped=$true; shape=@('PSP', 'BBB', 'PSP'); ing=[ordered]@{ P='OAK_PLANKS'; S='STICK'; B='BOOK' }; amount=1 } },

@{ id='wardrobe'; name='衣柜'; cat='储物'; hw=1.0; hh=2.0;
   tex=[ordered]@{ S=$M.BIRCH; D=$M.DARK; I=$M.IRON };
   box=@('0,0,2,16,32,14,S', '0,32,1,16,34,15,D', '1,4,14,7,28,15,S', '9,4,14,15,28,15,S', '6,15,15,8,17,16,I', '8,15,15,10,17,16,I');
   recipe=@{ shaped=$true; shape=@('SSS', 'SIS', 'SSS'); ing=[ordered]@{ S='BIRCH_PLANKS'; I='IRON_INGOT' }; amount=1 } },

@{ id='nightstand'; name='床头柜'; cat='储物'; hw=0.8; hh=1.0;
   tex=[ordered]@{ P=$M.OAK; D=$M.DARK; I=$M.IRON };
   box=@('1,0,2,15,9,14,P', '0,9,1,16,10,15,D', '2,2,14,14,7,15,D', '7,4,15,9,5,16,I');
   recipe=@{ shaped=$true; shape=@('PPP', 'PIP'); ing=[ordered]@{ P='OAK_PLANKS'; I='IRON_INGOT' }; amount=1 } },

@{ id='shoe_rack'; name='鞋架'; cat='储物'; hw=1.0; hh=1.0;
   tex=[ordered]@{ P=$M.BIRCH; };
   box=@('0,0,2,16,2,14,P', '0,8,2,16,10,14,P', '0,14,2,16,16,14,P', '0,0,2,2,16,14,P', '14,0,2,16,16,14,P');
   recipe=@{ shaped=$true; shape=@('PPP', 'L L'); ing=[ordered]@{ P='BIRCH_PLANKS'; L='STICK' }; amount=1 } },

@{ id='tv_stand'; name='电视柜'; cat='储物'; hw=1.0; hh=1.0;
   tex=[ordered]@{ D=$M.DARK; K=$M.BLACK; I=$M.IRON };
   box=@('0,0,2,16,10,14,D', '0,10,1,16,11,15,K', '1,2,14,7,8,15,D', '9,2,14,15,8,15,D', '3,5,15,5,6,16,I', '11,5,15,13,6,16,I');
   recipe=@{ shaped=$true; shape=@('PPP', 'PIP'); ing=[ordered]@{ P='DARK_OAK_PLANKS'; I='IRON_INGOT' }; amount=1 } },

@{ id='crate'; name='木箱'; cat='储物'; hw=1.0; hh=1.0;
   tex=[ordered]@{ P=$M.OAK; D=$M.DARK };
   box=@('0,0,1,16,14,15,P', '0,14,1,16,16,15,D', '0,4,1,16,5,15,D', '0,9,1,16,10,15,D');
   recipe=@{ shaped=$false; mats=@('OAK_PLANKS', 'OAK_PLANKS', 'OAK_PLANKS', 'OAK_PLANKS', 'STICK', 'STICK'); amount=1 } },

@{ id='vanity'; name='梳妆台'; cat='储物'; hw=1.0; hh=1.0;
   tex=[ordered]@{ Q=$M.QUARTZ; D=$M.DARK; G=$M.GLASS };
   box=@('0,0,2,16,12,14,Q', '0,12,1,16,13,15,Q', '4,13,2,12,28,3,D', '5,13,3,11,27,4,G', '1,3,14,7,9,15,D', '9,3,14,15,9,15,D');
   recipe=@{ shaped=$true; shape=@('QQQ', 'QPQ'); ing=[ordered]@{ Q='SMOOTH_QUARTZ'; P='OAK_PLANKS' }; amount=1 } },

@{ id='bookshelf_tall'; name='高书柜'; cat='储物'; hw=1.0; hh=2.0; storage=54;
   tex=[ordered]@{ P=$M.SPRUCE; D=$M.DARK; B=$M.BOOK };
   box=@('0,0,2,16,32,14,P', '0,32,1,16,34,15,D', '1,8,3,15,9,13,D', '1,16,3,15,17,13,D', '1,24,3,15,25,13,D', '2,9,4,14,16,12,B', '2,17,4,14,24,12,B', '2,25,4,14,31,12,B');
   recipe=@{ shaped=$true; shape=@('BBB', 'PPP', 'BBB'); ing=[ordered]@{ B='BOOK'; P='SPRUCE_PLANKS' }; amount=1 } },

@{ id='safe'; name='保险箱'; cat='储物'; hw=0.9; hh=1.0; storage=27;
   tex=[ordered]@{ I=$M.IRON; K=$M.BLACK; G=$M.GLOW };
   box=@('0,0,2,16,16,14,I', '1,2,13,15,14,15,K', '2,6,14,14,10,15,G', '7,4,15,9,6,16,G', '1,0,2,3,4,4,I', '13,0,2,15,4,4,I');
   recipe=@{ shaped=$true; shape=@('III', 'IGI', 'III'); ing=[ordered]@{ I='IRON_INGOT'; G='GLOWSTONE' }; amount=1 } },

@{ id='medicine_cabinet'; name='药柜'; cat='储物'; hw=0.9; hh=1.5; storage=18;
   tex=[ordered]@{ W=$M.WHITE_CON; D=$M.DARK; R=$M.RED; G=$M.GLASS };
   box=@('0,0,2,16,24,12,W', '0,24,1,16,26,13,W', '1,2,12,15,23,13,G', '6,8,13,10,18,14,R', '4,11,13,12,15,14,R', '14,10,13,15,11,14,D');
   recipe=@{ shaped=$true; shape=@('WWW', 'WGW', 'WWW'); ing=[ordered]@{ W='WHITE_CONCRETE'; G='GLASS' }; amount=1 } },

@{ id='wine_rack'; name='酒架'; cat='储物'; hw=0.9; hh=2.0; storage=18;
   tex=[ordered]@{ D=$M.DARK; G=$M.GREEN; R=$M.RED };
   box=@('0,0,2,16,28,14,D', '1,4,3,15,5,13,D', '1,12,3,15,13,13,D', '1,20,3,15,21,13,D', '2,5,5,4,11,8,G', '6,5,5,8,11,8,R', '10,5,5,12,11,8,G', '2,13,5,4,19,8,R');
   recipe=@{ shaped=$true; shape=@('L L', 'L L', 'DDD'); ing=[ordered]@{ L='STICK'; D='DARK_OAK_PLANKS' }; amount=1 } },

# ================= 床 =================
@{ id='bed'; name='单人床'; cat='床'; hw=1.0; hh=1.0; skipModel=$true; sd=2; seat=$true; seatY=0.26;
   tex=[ordered]@{ P=$M.OAK; R=$M.RED; W=$M.WHITE };
   recipe=@{ shaped=$true; shape=@('WWW', 'WPW', 'PPP'); ing=[ordered]@{ W='RED_WOOL'; P='OAK_PLANKS' }; amount=1 } },

@{ id='crib'; name='婴儿床'; cat='床'; hw=0.9; hh=1.0; sd=2;
   tex=[ordered]@{ P=$M.BIRCH; W=$M.WHITE };
   box=@('1,0,1,15,3,31,P', '2,3,2,14,6,30,W', '1,6,2,2,12,30,P', '14,6,2,15,12,30,P', '1,3,0,15,12,2,P', '1,3,30,15,9,32,P');
   recipe=@{ shaped=$true; shape=@('WWW', 'PPP', 'WWW'); ing=[ordered]@{ W='WHITE_WOOL'; P='BIRCH_PLANKS' }; amount=1 } },

# ================= 灯具 =================
@{ id='lamp'; name='落地灯'; cat='灯具'; hw=0.8; hh=1.0; skipModel=$true; light=$true;
   tex=[ordered]@{ I=$M.IRON; G=$M.GLOW };
   recipe=@{ shaped=$true; shape=@(' I ', ' I ', 'G  '); ing=[ordered]@{ I='IRON_INGOT'; G='GLOWSTONE' }; amount=1 } },

@{ id='table_lamp'; name='台灯'; cat='灯具'; hw=0.6; hh=0.9; light=$true;
   tex=[ordered]@{ I=$M.IRON; W=$M.WHITE; G=$M.GLOW };
   box=@('5,0,5,11,2,11,I', '7,2,7,9,10,9,I', '3,10,3,13,16,13,W', '4,16,4,12,17,12,G');
   recipe=@{ shaped=$true; shape=@('G  ', ' I ', ' I '); ing=[ordered]@{ G='GLOWSTONE'; I='STICK' }; amount=1 } },

@{ id='street_lamp'; name='路灯'; cat='灯具'; hw=0.8; hh=2.0; light=$true;
   tex=[ordered]@{ I=$M.IRON; S=$M.STONE; G=$M.GLOW };
   box=@('5,0,5,11,3,11,S', '7,3,7,9,44,9,I', '7,44,7,9,46,14,I', '5,38,10,11,44,16,G');
   recipe=@{ shaped=$true; shape=@('IGI', ' I ', ' I '); ing=[ordered]@{ I='IRON_INGOT'; G='GLOWSTONE' }; amount=1 } },

@{ id='lantern_post'; name='灯笼柱'; cat='灯具'; hw=0.7; hh=1.0; light=$true;
   tex=[ordered]@{ D=$M.DARK; G=$M.GLOW; I=$M.IRON };
   box=@('7,0,7,9,36,9,D', '2,30,7,14,32,9,D', '5,24,5,11,30,11,G', '4,30,4,12,32,12,I', '5,20,5,11,24,11,I');
   recipe=@{ shaped=$false; mats=@('DARK_OAK_LOG', 'GLOWSTONE', 'STICK', 'STICK'); amount=1 } },

@{ id='ceiling_lamp'; name='吊灯'; cat='灯具'; hw=0.8; hh=1.0; light=$true;
   tex=[ordered]@{ I=$M.IRON; W=$M.WHITE; G=$M.GLOW };
   box=@('7,24,7,9,32,9,I', '3,14,3,13,24,13,W', '4,15,4,12,23,12,G', '4,12,4,12,14,12,I', '4,10,4,12,12,12,G');
   recipe=@{ shaped=$true; shape=@('IGI', ' I '); ing=[ordered]@{ I='IRON_INGOT'; G='GLOWSTONE' }; amount=1 } },

@{ id='wall_lamp'; name='壁灯'; cat='灯具'; hw=0.6; hh=1.0; light=$true;
   tex=[ordered]@{ I=$M.IRON; W=$M.WHITE; G=$M.GLOW };
   box=@('6,10,10,10,14,16,I', '4,4,8,12,10,13,W', '5,2,9,11,4,12,G');
   recipe=@{ shaped=$true; shape=@(' I ', 'G  '); ing=[ordered]@{ I='IRON_INGOT'; G='GLOWSTONE' }; amount=1 } },

@{ id='candle'; name='烛台'; cat='灯具'; hw=0.5; hh=1.0; light=$true;
   tex=[ordered]@{ I=$M.IRON; W=$M.WHITE; G=$M.GLOW };
   box=@('4,0,6,12,2,10,I', '6,2,7,10,10,9,W', '6,10,8,10,12,8,G', '6,8,8,10,9,8,G');
   recipe=@{ shaped=$false; mats=@('IRON_INGOT', 'GLOWSTONE'); amount=1 } },

@{ id='neon_sign'; name='霓虹灯牌'; cat='灯具'; hw=0.8; hh=1.0; light=$true;
   tex=[ordered]@{ K=$M.BLACK; G=$M.GLOW; B=$M.BLUE };
   box=@('0,6,13,16,20,16,K', '1,8,14,15,18,15,G', '2,9,15,14,17,15,B');
   recipe=@{ shaped=$true; shape=@('GGG', 'GGG', 'KKK'); ing=[ordered]@{ G='GLOWSTONE'; K='BLACK_CONCRETE' }; amount=1 } },

# ================= 绿植装饰 =================
@{ id='plant'; name='盆栽'; cat='绿植'; hw=0.7; hh=1.0; skipModel=$true;
   tex=[ordered]@{ T=$M.POT; D=$M.DIRT; L=$M.LEAF };
   recipe=@{ shaped=$false; mats=@('FLOWER_POT', 'DIRT', 'OAK_LEAVES', 'OAK_LEAVES'); amount=1 } },

@{ id='flower_pot'; name='花盆'; cat='绿植'; hw=0.6; hh=1.0;
   tex=[ordered]@{ T=$M.POT; D=$M.DIRT; L=$M.LEAF; R=$M.RED };
   box=@('4,0,4,12,5,12,T', '3,5,3,13,6,13,T', '5,5,5,11,6,11,D', '4,6,4,12,11,12,L', '6,11,6,10,13,10,R');
   recipe=@{ shaped=$false; mats=@('TERRACOTTA', 'DIRT', 'OAK_LEAVES'); amount=1 } },

@{ id='planter_box'; name='花箱'; cat='绿植'; hw=1.0; hh=1.0;
   tex=[ordered]@{ P=$M.OAK; D=$M.DARK; S=$M.DIRT; L=$M.LEAF };
   box=@('0,0,2,16,6,14,P', '0,6,1,16,7,15,D', '1,6,2,15,7,13,S', '1,7,3,15,10,13,L');
   recipe=@{ shaped=$true; shape=@('PPP', 'DDD'); ing=[ordered]@{ P='OAK_PLANKS'; D='DIRT' }; amount=1 } },

@{ id='hedge'; name='绿篱'; cat='绿植'; hw=1.0; hh=1.0;
   tex=[ordered]@{ L=$M.LEAF; };
   box=@('0,0,0,16,12,16,L', '0,12,0,6,15,6,L', '10,12,10,16,16,16,L', '4,12,4,10,14,10,L');
   recipe=@{ shaped=$false; mats=@('OAK_LEAVES', 'OAK_LEAVES', 'OAK_LEAVES', 'DIRT'); amount=1 } },

@{ id='birdhouse'; name='鸟屋'; cat='绿植'; hw=0.7; hh=1.0;
   tex=[ordered]@{ P=$M.OAK; D=$M.DARK; K=$M.BLACK };
   box=@('7,0,7,9,20,9,D', '3,20,4,13,30,12,P', '2,30,3,14,34,13,D', '7,24,3,9,27,4,K');
   recipe=@{ shaped=$true; shape=@('PPP', 'P P', ' S '); ing=[ordered]@{ P='BIRCH_PLANKS'; S='STICK' }; amount=1 } },

@{ id='mailbox'; name='信箱'; cat='绿植'; hw=0.7; hh=1.0;
   tex=[ordered]@{ D=$M.DARK; I=$M.IRON; R=$M.RED };
   box=@('7,0,7,9,16,9,D', '4,16,4,12,24,12,I', '12,20,5,13,22,6,R');
   recipe=@{ shaped=$true; shape=@('III', ' S '); ing=[ordered]@{ I='IRON_INGOT'; S='STICK' }; amount=1 } },

@{ id='fence'; name='栅栏段'; cat='绿植'; hw=1.0; hh=1.0;
   tex=[ordered]@{ P=$M.OAK; };
   box=@('0,0,4,2,24,6,P', '14,0,4,16,24,6,P', '0,6,4,16,8,6,P', '0,14,4,16,16,6,P');
   recipe=@{ shaped=$false; mats=@('OAK_PLANKS', 'OAK_PLANKS', 'OAK_PLANKS', 'STICK'); amount=1 } },

@{ id='well'; name='水井'; cat='绿植'; hw=1.0; hh=1.0;
   tex=[ordered]@{ C=$M.COBBLE; W=$M.WATER; P=$M.OAK; D=$M.DARK };
   box=@('0,0,0,16,3,16,C', '0,3,0,4,10,16,C', '12,3,0,16,10,16,C', '0,3,0,16,10,4,C', '0,3,12,16,10,16,C', '4,3,4,12,4,12,W', '1,10,1,3,26,3,P', '13,10,1,15,26,3,P', '0,26,0,16,28,16,D');
   recipe=@{ shaped=$true; shape=@('CCC', 'CWC', 'CCC'); ing=[ordered]@{ C='COBBLESTONE'; W='WATER_BUCKET' }; amount=1 } },

@{ id='bush'; name='灌木'; cat='绿植'; hw=1.0; hh=1.0;
   tex=[ordered]@{ D=$M.DIRT; L=$M.LEAF };
   box=@('2,0,2,14,4,14,D', '0,4,0,16,12,16,L', '2,12,2,14,16,14,L', '2,10,2,5,14,5,L');
   recipe=@{ shaped=$false; mats=@('OAK_LEAVES', 'OAK_LEAVES', 'OAK_LEAVES', 'MOSS_BLOCK'); amount=1 } },

@{ id='cherry_bonsai'; name='樱花盆栽'; cat='绿植'; hw=0.7; hh=1.2;
   tex=[ordered]@{ T=$M.POT; D=$M.DIRT; C=$M.CHERRY; P=$M.CHERRY_P };
   box=@('2,0,2,14,6,14,T', '4,6,4,12,7,12,D', '7,7,7,9,16,9,P', '3,14,3,13,20,13,C', '5,18,5,11,22,11,C');
   recipe=@{ shaped=$false; mats=@('FLOWER_POT', 'DIRT', 'CHERRY_SAPLING', 'OAK_LEAVES'); amount=1 } },

@{ id='stone_lantern'; name='石灯笼'; cat='绿植'; hw=0.8; hh=1.2; light=$true;
   tex=[ordered]@{ C=$M.COBBLE; G=$M.GLOW; S=$M.STONE };
   box=@('6,0,6,10,2,10,C', '4,2,4,12,6,12,C', '5,6,5,11,14,11,G', '3,14,3,13,16,13,S', '7,16,7,9,19,9,C');
   recipe=@{ shaped=$true; shape=@('CCC', 'CGC', 'CCC'); ing=[ordered]@{ C='COBBLESTONE'; G='GLOWSTONE' }; amount=1 } },

@{ id='swing'; name='秋千'; cat='景观'; hw=1.0; hh=2.0; sw=2; seat=$true; seatY=0.7; seats=2;
   tex=[ordered]@{ D=$M.DARK; O=$M.OAK };
   box=@('0,0,2,3,26,4,D', '29,0,2,32,26,4,D', '0,26,2,32,28,4,D', '2,10,12,30,12,14,O', '1,12,12,3,26,13,D', '29,12,12,31,26,13,D');
   recipe=@{ shaped=$true; shape=@('DDD', 'S S'); ing=[ordered]@{ D='DARK_OAK_PLANKS'; S='STICK' }; amount=1 } },

# ================= 景观 =================
@{ id='garden_bench'; name='花园长椅'; cat='景观'; hw=1.0; hh=1.2; seat=$true; seatY=0.62;
   tex=[ordered]@{ P=$M.OAK; S=$M.STONE };
   box=@('0,8,2,16,10,14,P', '0,10,2,16,18,4,P', '1,0,3,4,8,5,S', '12,0,3,15,8,5,S');
   recipe=@{ shaped=$true; shape=@('PPP', 'S S'); ing=[ordered]@{ P='OAK_PLANKS'; S='STONE' }; amount=1 } },

@{ id='fountain'; name='喷泉'; cat='景观'; hw=1.0; hh=1.2;
   tex=[ordered]@{ S=$M.STONE; W=$M.WATER };
   box=@('0,0,0,16,6,16,S', '2,2,2,14,5,14,W', '6,5,6,10,14,10,S', '4,12,4,12,14,12,S', '7,14,7,9,20,9,W', '2,14,2,14,16,14,S');
   recipe=@{ shaped=$true; shape=@('SSS', 'SBS', 'SSS'); ing=[ordered]@{ S='STONE_BRICKS'; B='WATER_BUCKET' }; amount=1 } },

@{ id='arch'; name='花园拱门'; cat='景观'; hw=1.0; hh=2.0;
   tex=[ordered]@{ P=$M.OAK; L=$M.LEAF };
   box=@('0,0,4,3,28,12,P', '13,0,4,16,28,12,P', '0,28,4,16,31,12,P', '0,24,3,16,27,13,L');
   recipe=@{ shaped=$true; shape=@('P P', 'P P', 'PPP'); ing=[ordered]@{ P='OAK_PLANKS' }; amount=1 } },

@{ id='scarecrow'; name='稻草人'; cat='景观'; hw=0.8; hh=1.2;
   tex=[ordered]@{ D=$M.DARK; H=$M.HAY; K=$M.PUMPKIN };
   box=@('7,0,7,9,30,9,D', '4,8,5,12,20,11,H', '0,16,7,16,18,9,D', '5,20,5,11,28,11,K', '4,28,4,12,30,12,D');
   recipe=@{ shaped=$true; shape=@('K  ', 'HHH', 'L L'); ing=[ordered]@{ K='CARVED_PUMPKIN'; H='HAY_BLOCK'; L='STICK' }; amount=1 } },

# ================= 厨房 =================
@{ id='kitchen_counter'; name='厨房台面'; cat='厨房'; hw=1.0; hh=1.0;
   tex=[ordered]@{ Q=$M.QUARTZ; S=$M.STONE; D=$M.DARK };
   box=@('0,0,0,16,12,14,Q', '0,12,0,16,14,16,S', '1,2,14,7,10,15,D', '9,2,14,15,10,15,D');
   recipe=@{ shaped=$true; shape=@('QQQ', 'QIQ', 'QQQ'); ing=[ordered]@{ Q='SMOOTH_QUARTZ'; I='IRON_INGOT' }; amount=1 } },

@{ id='sink'; name='水槽'; cat='厨房'; hw=1.0; hh=1.0;
   tex=[ordered]@{ Q=$M.QUARTZ; I=$M.IRON; W=$M.WATER };
   box=@('0,0,0,16,12,14,Q', '0,12,0,16,14,16,Q', '2,10,2,14,12,12,I', '3,12,3,13,13,13,W', '7,14,2,9,20,4,I');
   recipe=@{ shaped=$true; shape=@('III', 'QQQ'); ing=[ordered]@{ I='IRON_INGOT'; Q='SMOOTH_QUARTZ' }; amount=1 } },

@{ id='fridge'; name='冰箱'; cat='厨房'; hw=1.0; hh=2.0;
   tex=[ordered]@{ W=$M.WHITE_CON; I=$M.IRON };
   box=@('0,0,2,16,30,14,W', '0,30,2,16,32,14,I', '1,2,14,15,15,15,W', '1,16,14,15,29,15,W', '13,4,15,14,13,16,I');
   recipe=@{ shaped=$true; shape=@('III', 'I I', 'III'); ing=[ordered]@{ I='IRON_INGOT' }; amount=1 } },

@{ id='stove'; name='灶台'; cat='厨房'; hw=1.0; hh=1.0;
   tex=[ordered]@{ I=$M.IRON; K=$M.BLACK; R=$M.RED };
   box=@('0,0,0,16,12,14,I', '0,12,0,16,14,16,K', '2,14,2,6,15,6,K', '10,14,10,14,15,14,K', '1,2,14,15,8,15,K', '2,9,14,4,11,15,R', '13,9,14,15,11,15,R');
   recipe=@{ shaped=$true; shape=@('III', 'ICI', 'III'); ing=[ordered]@{ I='IRON_INGOT'; C='COAL' }; amount=1 } },

@{ id='oven'; name='烤箱'; cat='厨房'; hw=1.0; hh=2.0;
   tex=[ordered]@{ K=$M.BLACK; G=$M.GLOW; I=$M.IRON; W=$M.WHITE_CON };
   box=@('0,0,2,16,26,14,K', '1,4,14,15,18,15,K', '3,6,14,13,16,15,G', '1,19,14,15,21,15,I', '2,22,14,14,25,15,W');
   recipe=@{ shaped=$true; shape=@('KKK', 'KIK', 'KKK'); ing=[ordered]@{ K='IRON_BLOCK'; I='IRON_INGOT' }; amount=1 } },

@{ id='coffee_machine'; name='咖啡机'; cat='厨房'; hw=0.6; hh=1.0;
   tex=[ordered]@{ K=$M.BLACK; G=$M.GLASS; I=$M.IRON; W=$M.WHITE_CON };
   box=@('1,0,3,15,28,12,K', '2,20,12,8,27,13,G', '10,10,12,14,14,13,I', '8,14,13,11,18,14,W', '2,28,4,14,30,11,I');
   recipe=@{ shaped=$false; mats=@('IRON_INGOT', 'GLASS', 'IRON_INGOT', 'BLACK_CONCRETE'); amount=1 } },

# ================= 其他 =================
@{ id='clock'; name='落地钟'; cat='其他'; hw=0.9; hh=2.0;
   tex=[ordered]@{ D=$M.DARK; K=$M.BLACK; W=$M.WHITE_CON };
   box=@('0,0,1,16,6,15,D', '0,0,2,16,44,14,D', '0,44,1,16,47,15,D', '2,32,14,14,44,15,W', '4,10,14,12,30,15,K');
   recipe=@{ shaped=$true; shape=@('DDD', 'DGD', 'DDD'); ing=[ordered]@{ D='DARK_OAK_PLANKS'; G='GLOWSTONE' }; amount=1 } },

@{ id='coat_rack'; name='衣帽架'; cat='其他'; hw=0.5; hh=2.0;
   tex=[ordered]@{ D=$M.DARK };
   box=@('3,0,3,13,2,13,D', '7,0,7,9,40,9,D', '3,30,7,7,32,8,D', '9,30,7,13,32,8,D', '7,30,3,8,32,7,D', '7,30,9,8,32,13,D');
   recipe=@{ shaped=$true; shape=@('I I', ' I ', ' I '); ing=[ordered]@{ I='IRON_INGOT' }; amount=1 } },

@{ id='rug'; name='地毯'; cat='其他'; hw=1.0; hh=0.3;
   tex=[ordered]@{ R=$M.RED; W=$M.WHITE };
   box=@('0,0,0,16,1,16,R', '3,1,3,13,2,13,W');
   recipe=@{ shaped=$true; shape=@('RRR', 'RRR'); ing=[ordered]@{ R='RED_WOOL' }; amount=1 } },

@{ id='tv'; name='电视'; cat='其他'; hw=1.0; hh=1.0;
   tex=[ordered]@{ D=$M.DARK; K=$M.BLACK };
   box=@('0,0,3,16,6,13,D', '1,0,4,15,1,12,D', '0,6,4,16,26,8,K', '0,6,3,16,26,4,D');
   recipe=@{ shaped=$true; shape=@('KKK', 'KKK', 'D D'); ing=[ordered]@{ K='BLACK_CONCRETE'; D='DARK_OAK_PLANKS' }; amount=1 } },

@{ id='fireplace'; name='壁炉'; cat='其他'; hw=1.0; hh=2.0; light=$true;
   tex=[ordered]@{ B=$M.BRICK; K=$M.BLACK; G=$M.GLOW; D=$M.DARK };
   box=@('0,0,0,16,4,14,B', '0,4,0,16,26,10,B', '4,4,10,12,18,11,K', '5,4,9,11,10,11,G', '0,26,0,16,28,12,D');
   recipe=@{ shaped=$true; shape=@('BBB', 'B B', 'BGB'); ing=[ordered]@{ B='BRICKS'; G='GLOWSTONE' }; amount=1 } },

@{ id='piano'; name='钢琴'; cat='其他'; hw=1.0; hh=1.2;
   tex=[ordered]@{ K=$M.BLACK; W=$M.WHITE_CON; D=$M.DARK };
   box=@('0,0,2,16,4,12,K', '0,4,2,16,24,12,K', '1,14,12,15,16,14,W', '0,24,1,16,26,13,D', '2,26,2,14,30,3,D');
   recipe=@{ shaped=$true; shape=@('DDD', 'DDD', 'K K'); ing=[ordered]@{ D='DARK_OAK_PLANKS'; K='BLACK_CONCRETE' }; amount=1 } },

@{ id='mirror'; name='落地镜'; cat='其他'; hw=1.0; hh=2.0;
   tex=[ordered]@{ D=$M.DARK; G=$M.GLASS; Q=$M.QUARTZ };
   box=@('1,0,26,15,30,28,D', '2,2,25,14,28,26,G', '2,28,25,14,30,26,Q', '2,0,25,14,2,26,Q');
   recipe=@{ shaped=$true; shape=@('GGG', 'GGG', 'DDD'); ing=[ordered]@{ G='GLASS'; D='DARK_OAK_PLANKS' }; amount=1 } },

@{ id='painting_frame'; name='相框'; cat='其他'; hw=0.8; hh=1.0;
   tex=[ordered]@{ D=$M.DARK; B=$M.BRICK; W=$M.WHITE_CON };
   box=@('0,4,14,16,20,16,D', '2,6,15,14,18,16,B', '4,8,15,12,16,16,W');
   recipe=@{ shaped=$true; shape=@('D D', 'DDD', 'D D'); ing=[ordered]@{ D='DARK_OAK_PLANKS' }; amount=1 } }
)

# ------------------------------------------------------------------ 生成模型
$generated = 0
foreach ($item in $items) {
    if ($item.ContainsKey('skipModel') -and $item.skipModel) { continue }
    $tex = [ordered]@{}
    foreach ($k in $item.tex.Keys) { $tex[$k] = $item.tex[$k] }
    $elements = @()
    foreach ($spec in $item.box) { $elements += (New-Box $spec) }
    $model = [ordered]@{
        texture_size = @(16, 16)
        gui_light    = 'front'
        textures     = $tex
        elements     = $elements
    }
    $path = Join-Path $modelDir "$($item.id).json"
    [System.IO.File]::WriteAllText($path, ($model | ConvertTo-Json -Depth 12), [System.Text.UTF8Encoding]::new($false))
    $generated++
}

# ------------------------------------------------------------------ 生成 furniture.yml
$lines = New-Object System.Collections.Generic.List[string]
$lines.Add('# ===== 家具定义（由 tools/gen-catalog.ps1 生成，手改后也可以直接跑插件） =====')
$lines.Add('#   name          显示名（支持 & 颜色代码）')
$lines.Add('#   model         资源包里的 item model，格式 命名空间:模型名（对应 assets/命名空间/models/模型名.json）')
$lines.Add('#   material      物品本体材质（只影响背包图标底子，模型由 model 决定）')
$lines.Add('#   hitbox-width  交互碰撞箱宽度（格）')
$lines.Add('#   hitbox-height 交互碰撞箱高度（格）')
$lines.Add('#   view-range    显示实体的可见范围倍率（1.0 为默认）')
$lines.Add('#   transform     ItemDisplay 的显示变换上下文，默认 NONE（按方块 1:1 比例渲染）')
$lines.Add('#   scale         模型缩放，默认 1.0')
$lines.Add('#   offset-y      模型垂直偏移（格），默认 0（模型 y=0 贴地）')
$lines.Add('#   size-width    占地宽度（格，模型局部 +x 方向），多格家具用 2/3')
$lines.Add('#   size-depth    占地深度（格，模型局部 +z 方向）')
$lines.Add('#   seat          能否坐人（右键坐下），配 seat-y 指定坐姿高度')
$lines.Add('#   seats         可同时坐几人（默认 1，长凳/沙发可 2~3）')
$lines.Add('#   seat-y        坐姿高度（相对脚下方块底部，格；0.45~0.65 常见）')
$lines.Add('#   light         能否开关灯（右键切换模型亮度，同时点亮周围）')
$lines.Add('#   storage       容器槽位（9/18/27/36/54，0 或省略 = 不能储物）')
$lines.Add('#   recipe        工作台合成配方；shaped: true 用 shape + ingredients，shaped: false 直接列材质')
$lines.Add('')
$lines.Add('furniture:')

# 老家具的容器格数（按 ID 补），新家具直接在条目里写 storage
$storageMap = @{
    cabinet = 27; drawer = 27; shelf = 27; wardrobe = 54; nightstand = 9
    tv_stand = 27; crate = 27; vanity = 27; shoe_rack = 9; fridge = 54; kitchen_counter = 9
}

$currentCat = ''
foreach ($item in $items) {
    if ($item.cat -ne $currentCat) {
        $currentCat = $item.cat
        $lines.Add('')
        $lines.Add("  # ---- $currentCat ----")
    }
    $hw = if ($item.ContainsKey('hw')) { $item.hw } else { 1.0 }
    $hh = if ($item.ContainsKey('hh')) { $item.hh } else { 1.0 }
    $hwText = ([double]$hw).ToString('0.0#', [Globalization.CultureInfo]::InvariantCulture)
    $hhText = ([double]$hh).ToString('0.0#', [Globalization.CultureInfo]::InvariantCulture)
    $lines.Add("  $($item.id):")
    $lines.Add("    name: `"&f$($item.name)`"")
    $lines.Add("    model: `"furniture:$($item.id)`"")
    $lines.Add('    material: PAPER')
    $lines.Add("    hitbox-width: $hwText")
    $lines.Add("    hitbox-height: $hhText")
    $lines.Add('    view-range: 1.0')
    $lines.Add('    transform: NONE')
    $lines.Add('    scale: 1.0')
    $lines.Add('    offset-y: 0.0')
    $sw = if ($item.ContainsKey('sw')) { [int]$item.sw } else { 1 }
    $sd = if ($item.ContainsKey('sd')) { [int]$item.sd } else { 1 }
    if ($sw -ne 1) { $lines.Add("    size-width: $sw") }
    if ($sd -ne 1) { $lines.Add("    size-depth: $sd") }
    if ($item.ContainsKey('seat') -and $item.seat) {
        $lines.Add('    seat: true')
        $seatYText = if ($item.ContainsKey('seatY')) { ([double]$item.seatY).ToString('0.0#', [Globalization.CultureInfo]::InvariantCulture) } else { '0.6' }
        $lines.Add("    seat-y: $seatYText")
        if ($item.ContainsKey('seats')) { $lines.Add('    seats: ' + $item.seats) }
    }
    if ($item.ContainsKey('light') -and $item.light) {
        $lines.Add('    light: true')
    }
    $storage = if ($item.ContainsKey('storage')) { [int]$item.storage } elseif ($storageMap.ContainsKey($item.id)) { [int]$storageMap[$item.id] } else { 0 }
    if ($storage -gt 0) {
        $lines.Add("    storage: $storage")
    }
    $lines.Add('    recipe:')
    $rec = $item.recipe
    if ($rec.shaped) {
        $lines.Add('      shaped: true')
        $lines.Add('      shape:')
        foreach ($row in $rec.shape) { $lines.Add("        - `"$row`"") }
        $lines.Add('      ingredients:')
        foreach ($k in $rec.ing.Keys) { $lines.Add("        $k`: $($rec.ing[$k])") }
    } else {
        $lines.Add('      shaped: false')
        $lines.Add('      ingredients:')
        foreach ($m in $rec.mats) { $lines.Add("        - $m") }
    }
    $lines.Add("      amount: $($rec.amount)")
    $lines.Add('')
}

[System.IO.File]::WriteAllText($yamlPath, ($lines -join "`r`n") + "`r`n", [System.Text.UTF8Encoding]::new($false))
"生成模型 $generated 个, YAML 条目 $($items.Count) 条"



