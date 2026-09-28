from setuptools import find_packages, setup

setup(
    name="source_paper",
    version="0.1.0",
    description="Deterministic paper fill source for Puente.",
    packages=find_packages(),
    install_requires=["airbyte-cdk~=0.2"],
    package_data={"": ["*.json", "fixtures/*.json", "schemas/*.json"]},
    extras_require={"tests": ["pytest~=7.2"]},
)
