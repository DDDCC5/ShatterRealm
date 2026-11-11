#include <assimp/Importer.hpp>
#include <assimp/Exporter.hpp>
#include <assimp/scene.h>
#include <assimp/postprocess.h>
#include <assimp/material.h>
#include <manifold/manifold.h>
#include <rapidjson/stringbuffer.h>
#include <rapidjson/writer.h>
#include <filesystem>
#include <fstream>
#include <stdexcept>
#include <random>
#include <algorithm>
#include <cmath>
#ifdef __ANDROID__
#include <jni.h>
#endif
using manifold::Manifold;
using manifold::MeshGL;
using manifold::vec3;
namespace fs=std::filesystem;

static vec3 position(const MeshGL& m,size_t v) {
    return {m.vertProperties[v*m.numProp],m.vertProperties[v*m.numProp+1],m.vertProperties[v*m.numProp+2]};
}
static vec3 cross(vec3 a,vec3 b) { return {a.y*b.z-a.z*b.y,a.z*b.x-a.x*b.z,a.x*b.y-a.y*b.x}; }
static double dot(vec3 a,vec3 b) { return a.x*b.x+a.y*b.y+a.z*b.z; }
static vec3 centerOfMass(const Manifold& solid) {
    auto m=solid.GetMeshGL();double volume=0;vec3 weighted(0.0);
    for(size_t i=0;i<m.triVerts.size();i+=3) {
        auto a=position(m,m.triVerts[i]),b=position(m,m.triVerts[i+1]),c=position(m,m.triVerts[i+2]);
        double v=dot(a,cross(b,c))/6;volume+=v;weighted+=(a+b+c)*(v/4);
    }
    if(std::abs(volume)<1e-12) throw std::runtime_error("Model has no solid volume");
    return weighted/volume;
}
static aiMesh* makeMesh(const Manifold& solid) {
    auto m=solid.GetMeshGL();auto out=new aiMesh();out->mPrimitiveTypes=aiPrimitiveType_TRIANGLE;
    out->mNumVertices=m.NumVert();out->mVertices=new aiVector3D[out->mNumVertices];
    out->mColors[0]=new aiColor4D[out->mNumVertices];
    for(unsigned i=0;i<out->mNumVertices;i++) {
        auto p=position(m,i);out->mVertices[i]={float(p.x),float(p.y),float(p.z)};
        out->mColors[0][i]=m.numProp>=7 ? aiColor4D(m.vertProperties[i*m.numProp+3],m.vertProperties[i*m.numProp+4],m.vertProperties[i*m.numProp+5],m.vertProperties[i*m.numProp+6]) : aiColor4D(.72,.75,.74,1);
    }
    out->mNumFaces=m.NumTri();out->mFaces=new aiFace[out->mNumFaces];
    for(unsigned i=0;i<out->mNumFaces;i++) {
        auto& f=out->mFaces[i];f.mNumIndices=3;f.mIndices=new unsigned[3];
        for(unsigned j=0;j<3;j++) f.mIndices[j]=m.triVerts[i*3+j];
    }
    return out;
}
static void exportSolids(const std::vector<Manifold>& solids,const std::vector<vec3>& offsets,const fs::path& file,bool hullNames) {
    aiScene scene;scene.mRootNode=new aiNode("root");scene.mNumMaterials=1;
    scene.mMaterials=new aiMaterial*[1]{new aiMaterial()};
    aiColor4D color(1,1,1,1);scene.mMaterials[0]->AddProperty(&color,1,AI_MATKEY_BASE_COLOR);
    scene.mNumMeshes=solids.size();scene.mMeshes=new aiMesh*[scene.mNumMeshes];
    scene.mRootNode->mNumChildren=solids.size();scene.mRootNode->mChildren=new aiNode*[solids.size()];
    for(size_t i=0;i<solids.size();i++) {
        scene.mMeshes[i]=makeMesh(solids[i]);char name[80];snprintf(name,sizeof(name),hullNames?"fragment-%03zu-hull-00":"fragment-%03zu",i);
        auto node=new aiNode(name);node->mParent=scene.mRootNode;node->mNumMeshes=1;node->mMeshes=new unsigned[1]{unsigned(i)};
        node->mTransformation.a4=offsets[i].x;node->mTransformation.b4=offsets[i].y;node->mTransformation.c4=offsets[i].z;
        scene.mRootNode->mChildren[i]=node;
    }
    Assimp::Exporter exporter;
    if(exporter.Export(&scene,"glb2",file.string(),aiProcess_GenNormals)!=AI_SUCCESS) throw std::runtime_error(exporter.GetErrorString());
}
static void convert(const fs::path& input,const fs::path& output) {
    if(fs::file_size(input)>64*1024*1024) throw std::runtime_error("Model exceeds 64 MiB");
    Assimp::Importer importer;
    const aiScene* scene=importer.ReadFile(input.string(),aiProcess_Triangulate|aiProcess_JoinIdenticalVertices|aiProcess_PreTransformVertices|aiProcess_GenNormals|aiProcess_ValidateDataStructure);
    if(!scene || !scene->HasMeshes()) throw std::runtime_error(importer.GetErrorString());
    unsigned faces=0;for(unsigned i=0;i<scene->mNumMeshes;i++) faces+=scene->mMeshes[i]->mNumFaces;
    if(faces>150000) throw std::runtime_error("Model exceeds 150000 triangles");
    std::string ext=input.extension().string();std::transform(ext.begin(),ext.end(),ext.begin(),::tolower);
    // Assimp's FBX root already incorporates UnitScaleFactor in centimeters and source axes.
    // After PreTransformVertices only the cm-to-m factor remains. GLB and OBJ are meters.
    double scale=ext==".fbx" ?.01:1.;
    MeshGL mesh;mesh.numProp=7;
    for(unsigned k=0;k<scene->mNumMeshes;k++) {
        const auto* src=scene->mMeshes[k];unsigned base=mesh.NumVert();aiColor4D color(.72f,.75f,.74f,1);
        scene->mMaterials[src->mMaterialIndex]->Get(AI_MATKEY_BASE_COLOR,color);
        if(color==aiColor4D(.72f,.75f,.74f,1)) scene->mMaterials[src->mMaterialIndex]->Get(AI_MATKEY_COLOR_DIFFUSE,color);
        for(unsigned v=0;v<src->mNumVertices;v++) {
            auto p=src->mVertices[v];auto c=src->HasVertexColors(0)?src->mColors[0][v]:color;
            if(!std::isfinite(p.x)||!std::isfinite(p.y)||!std::isfinite(p.z)) throw std::runtime_error("Nonfinite vertex");
            mesh.vertProperties.insert(mesh.vertProperties.end(),{float(p.x*scale),float(p.y*scale),float(p.z*scale),c.r,c.g,c.b,c.a});
        }
        for(unsigned f=0;f<src->mNumFaces;f++) { if(src->mFaces[f].mNumIndices!=3) continue;
            for(unsigned v=0;v<3;v++) mesh.triVerts.push_back(base+src->mFaces[f].mIndices[v]); }
    }
    mesh.Merge();Manifold solid(mesh);
    if(solid.Status()!=Manifold::Error::NoError || solid.IsEmpty()) throw std::runtime_error("Model must be a closed orientable solid");
    const double originalVolume=solid.Volume();if(originalVolume<=1e-12) throw std::runtime_error("Zero volume model");
    auto box=solid.BoundingBox();auto size=box.Size();
    if(std::max({size.x,size.y,size.z})>2.0) throw std::runtime_error("Model exceeds 2 m; export intended units");
    auto shift=vec3(-(box.min.x+box.max.x)/2,-box.min.y,-(box.min.z+box.max.z)/2);solid=solid.Translate(shift);
    std::vector<Manifold> pieces=solid.Decompose();
    if(pieces.size()>96) throw std::runtime_error("Too many disconnected components");
    std::mt19937 random(0x5348524c);std::uniform_real_distribution<double> direction(-1,1);
    for(int attempt=0;pieces.size()<28 && attempt<100;attempt++) {
        auto it=std::max_element(pieces.begin(),pieces.end(),[](const auto& a,const auto& b){return a.Volume()<b.Volume();});
        auto center=it->BoundingBox().Center();vec3 normal(direction(random),direction(random),direction(random));
        if(dot(normal,normal)<.01) continue;
        auto parts=it->SplitByPlane(normal,dot(normal,center));
        if(parts.first.IsEmpty()||parts.second.IsEmpty()||parts.first.Volume()<originalVolume*1e-5||parts.second.Volume()<originalVolume*1e-5) continue;
        *it=parts.first;pieces.push_back(parts.second);
    }
    double sum=0;std::vector<vec3> offsets;std::vector<Manifold> local;
    for(auto& piece:pieces) { sum+=piece.Volume();auto center=centerOfMass(piece);offsets.push_back(center);local.push_back(piece.Translate(-center)); }
    if(std::abs(sum-originalVolume)/originalVolume>1e-5) throw std::runtime_error("Fracture volume mismatch");
    fs::create_directories(output);
    exportSolids({solid},{vec3(0.0)},output/"intact.glb",false);
    exportSolids(local,offsets,output/"fragments.glb",false);
    exportSolids(local,offsets,output/"collision-hulls.glb",true);
    rapidjson::StringBuffer hullBuffer;rapidjson::Writer<rapidjson::StringBuffer> hw(hullBuffer);
    hw.StartArray();
    for(size_t i=0;i<local.size();i++) {
        auto mesh=local[i].GetMeshGL();char name[80];snprintf(name,sizeof(name),"fragment-%03zu-hull-00",i);
        hw.StartObject();hw.Key("name");hw.String(name);hw.Key("positions");hw.StartArray();
        for(size_t v=0;v<mesh.NumVert();v++) {auto p=position(mesh,v);hw.StartArray();hw.Double(p.x);hw.Double(p.y);hw.Double(p.z);hw.EndArray();}
        hw.EndArray();hw.Key("indices");hw.StartArray();for(auto index:mesh.triVerts) hw.Uint(index);hw.EndArray();hw.EndObject();
    }
    hw.EndArray();std::ofstream(output/"colliders.json")<<hullBuffer.GetString();
    rapidjson::StringBuffer buffer;rapidjson::Writer<rapidjson::StringBuffer> w(buffer);
    w.StartObject();w.Key("massKg");w.Double(originalVolume*1000);w.Key("fragmentRecords");w.StartArray();
    for(size_t i=0;i<local.size();i++) { char id[64];snprintf(id,sizeof(id),"fragment-%03zu",i);w.StartObject();
        w.Key("id");w.String(id);w.Key("positionM");w.StartArray();w.Double(offsets[i].x);w.Double(offsets[i].y);w.Double(offsets[i].z);w.EndArray();
        w.Key("massKg");w.Double(local[i].Volume()*1000);w.EndObject(); }
    w.EndArray();w.Key("sourceVolumeM3");w.Double(originalVolume);w.Key("fragmentVolumeM3");w.Double(sum);w.Key("sourceFormat");w.String(ext.c_str());w.EndObject();
    std::ofstream(output/"metadata.json")<<buffer.GetString();
}
#ifdef __ANDROID__
extern "C" JNIEXPORT void JNICALL Java_com_example_shatterrealm_platform_CustomModelImporter_convertNative(JNIEnv* env,jobject,jstring input,jstring output) {
    const char* a=env->GetStringUTFChars(input,nullptr);const char* b=env->GetStringUTFChars(output,nullptr);
    try { convert(a,b); } catch(const std::exception& e) {env->ThrowNew(env->FindClass("java/io/IOException"),e.what());}
    env->ReleaseStringUTFChars(input,a);env->ReleaseStringUTFChars(output,b);
}
#else
#include <iostream>
int main(int argc,char** argv) { if(argc!=3) return 2;try {convert(argv[1],argv[2]);return 0;} catch(const std::exception& e){std::cerr<<e.what()<<std::endl;return 1;} }
#endif
